"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { toast } from "sonner";
import {
  isRiskyFile,
  validateFileSize,
  RESUMABLE_THRESHOLD,
} from "@/lib/constants";
import {
  initPresignedUpload,
  confirmUpload,
  getFileStatus,
  initMultipartUpload,
  getPartUrl,
  completePart,
  completeMultipart,
  abortMultipart,
  getMultipartSession,
  minioClient,
} from "@/lib/api/upload";
import {
  savePendingMultipart,
  removePendingMultipart,
  getPendingMultiparts,
} from "@/lib/utils/upload-storage";
import {
  calcTotalParts,
  slicePart,
  buildPartList,
  getMissingParts,
  sleep,
} from "@/lib/utils/upload-utils";
import type { UploadItem, UploadStatus, PartInfo, PendingMultipartRecord } from "@/types/upload/upload.types";

// Maximum concurrent chunk uploads per multipart file
const MULTIPART_CONCURRENCY = 3;
// Polling interval for file status after confirm/complete (ms)
const POLL_INTERVAL_MS = 2000;
// Maximum polling duration (3 minutes)
const POLL_TIMEOUT_MS = 3 * 60 * 1000;

// ─── Helpers ─────────────────────────────────────────────────────────────────

function createItem(file: File, folderId?: string): UploadItem {
  return {
    id: crypto.randomUUID(),
    file,
    folderId,
    status: "queued",
    progress: 0,
    uploadedParts: [],
  };
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * Internal hook — consumed via UploadContext so state persists across navigation.
 * Do NOT call this directly in components; use useUpload() from upload-context.tsx.
 */
export function useFileUploadInternal(options?: { onUploadDone?: () => void }) {
  const [uploadQueue, setUploadQueue] = useState<UploadItem[]>([]);

  const onUploadDoneRef = useRef(options?.onUploadDone);
  useEffect(() => { onUploadDoneRef.current = options?.onUploadDone; }, [options?.onUploadDone]);

  // ── Queue helpers ─────────────────────────────────────────────────────────

  const updateItem = useCallback(
    (itemId: string, patch: Partial<UploadItem>) => {
      setUploadQueue((prev) =>
        prev.map((item) => (item.id === itemId ? { ...item, ...patch } : item))
      );
    },
    []
  );

  // ── Status polling ────────────────────────────────────────────────────────

  const pollFileStatus = useCallback(
    async (fileId: string, itemId: string) => {
      const deadline = Date.now() + POLL_TIMEOUT_MS;
      while (Date.now() < deadline) {
        await sleep(POLL_INTERVAL_MS);
        try {
          const res = await getFileStatus(fileId);
          // ⚠️ response field is `fileId` not `id` per FileRestMapper
          if (res.status === "ACTIVE") {
            updateItem(itemId, { status: "done", progress: 100 });
            onUploadDoneRef.current?.();
            return;
          }
          if (res.status === "INFECTED") {
            updateItem(itemId, { status: "infected", errorMessage: "Virus detected in file" });
            return;
          }
          if (res.status === "UPLOAD_FAILED") {
            updateItem(itemId, { status: "error", errorMessage: "Upload failed during processing" });
            return;
          }
          // SCANNING — keep polling
          updateItem(itemId, { status: "scanning" });
        } catch {
          // Transient network error — keep polling until deadline
        }
      }
      updateItem(itemId, { status: "error", errorMessage: "Scan timeout — please retry" });
    },
    [updateItem]
  );

  // ── Presigned upload (file < RESUMABLE_THRESHOLD) ─────────────────────────

  const runPresignedUpload = useCallback(
    async (item: UploadItem) => {
      const abortController = new AbortController();
      updateItem(item.id, { status: "uploading", progress: 0, abortController });

      try {
        // Step 1: init
        const { fileId, uploadUrl } = await initPresignedUpload({
          originalName: item.file.name,
          mimeType: item.file.type || "application/octet-stream",
          sizeBytes: item.file.size,
          folderId: item.folderId,
        });
        updateItem(item.id, { fileId, uploadUrl });

        // Step 2: PUT directly to MinIO (bypass BFF proxy)
        const startTime = Date.now();

        await minioClient.put(uploadUrl, item.file, {
          headers: { "Content-Type": item.file.type || "application/octet-stream" },
          signal: abortController.signal,
          onUploadProgress: (evt) => {
            if (!evt.total) return;
            const loaded = evt.loaded;
            const elapsed = (Date.now() - startTime) / 1000;
            const speedBps = elapsed > 0 ? loaded / elapsed : 0;
            const remaining = evt.total - loaded;
            const etaSeconds = speedBps > 0 ? remaining / speedBps : undefined;
            updateItem(item.id, {
              progress: Math.round((loaded / evt.total) * 100),
              speedBps,
              etaSeconds,
            });
          },
        });

        // Step 3: confirm upload
        updateItem(item.id, { status: "confirming", progress: 100 });
        await confirmUpload(fileId);

        // Step 4: poll for scan result
        updateItem(item.id, { status: "scanning" });
        await pollFileStatus(fileId, item.id);
      } catch (err: unknown) {
        if (isAbortError(err)) {
          updateItem(item.id, { status: "cancelled" });
        } else {
          const msg = err instanceof Error ? err.message : "Upload failed";
          updateItem(item.id, { status: "error", errorMessage: msg });
        }
      }
    },
    [updateItem, pollFileStatus]
  );

  // ── Multipart upload (file >= RESUMABLE_THRESHOLD) ────────────────────────

  const runMultipartUpload = useCallback(
    async (item: UploadItem, resumeFromParts?: PartInfo[]) => {
      const abortController = new AbortController();
      updateItem(item.id, { status: "uploading", abortController });

      try {
        let uploadId = item.uploadId;
        let fileId = item.fileId;
        const totalParts = item.totalParts ?? calcTotalParts(item.file.size);
        let completedParts: PartInfo[] = resumeFromParts ? [...resumeFromParts] : [];

        // Step 1: init (only if no existing session)
        if (!uploadId || !fileId) {
          const init = await initMultipartUpload({
            originalName: item.file.name,
            mimeType: item.file.type || "application/octet-stream",
            totalSizeBytes: item.file.size,
            totalParts,
            folderId: item.folderId,
          });
          uploadId = init.uploadId;
          fileId = init.fileId;

          updateItem(item.id, {
            uploadId,
            fileId,
            totalParts,
            sessionExpiresAt: init.expiresAt,
          });

          // Persist to localStorage for resume after refresh
          savePendingMultipart({
            itemId: item.id,
            uploadId,
            fileId,
            fileName: item.file.name,
            fileSizeBytes: item.file.size,
            mimeType: item.file.type || "application/octet-stream",
            folderId: item.folderId,
            startedAt: new Date().toISOString(),
          });
        }

        // Determine remaining parts
        const missingParts = getMissingParts(totalParts, completedParts);
        const startTime = Date.now();
        const totalUploaded = completedParts.reduce(
          (acc, p) => acc + slicePart(item.file, p.partNumber).size,
          0
        );
        let bytesUploaded = totalUploaded;

        // Step 2: upload chunks with concurrency limit
        let partIdx = 0;
        const uploadChunk = async (partNumber: number): Promise<void> => {
          if (abortController.signal.aborted) return;

          // Get fresh presigned part URL (avoids 30-min TTL expiry)
          const { partUrl } = await getPartUrl(uploadId!, partNumber);
          const blob = slicePart(item.file, partNumber);

          const chunkRes = await minioClient.put(partUrl, blob, {
            headers: { "Content-Type": item.file.type || "application/octet-stream" },
            signal: abortController.signal,
          });

          // Extract ETag from response header
          const rawEtag = (chunkRes.headers as Record<string, string>)["etag"] ?? "";
          const etag = rawEtag.replace(/"/g, "");

          // Notify backend that this part is complete
          await completePart(uploadId!, partNumber, etag);

          const part: PartInfo = { partNumber, etag };
          completedParts = [...completedParts, part];

          bytesUploaded += blob.size;
          const elapsed = (Date.now() - startTime) / 1000;
          const speedBps = elapsed > 0 ? (bytesUploaded - totalUploaded) / elapsed : 0;
          const remaining = item.file.size - bytesUploaded;
          const etaSeconds = speedBps > 0 ? remaining / speedBps : undefined;

          updateItem(item.id, {
            uploadedParts: completedParts,
            progress: Math.round((bytesUploaded / item.file.size) * 100),
            speedBps,
            etaSeconds,
          });
        };

        // Process chunks in batches of MULTIPART_CONCURRENCY
        while (partIdx < missingParts.length) {
          if (abortController.signal.aborted) break;
          const batch = missingParts.slice(partIdx, partIdx + MULTIPART_CONCURRENCY);
          await Promise.all(batch.map(uploadChunk));
          partIdx += MULTIPART_CONCURRENCY;
        }

        if (abortController.signal.aborted) {
          updateItem(item.id, { status: "cancelled" });
          return;
        }

        // Step 3: complete multipart — all parts must be uploaded
        const sortedParts = buildPartList(completedParts);
        updateItem(item.id, { status: "confirming", progress: 100 });
        await completeMultipart(uploadId!, sortedParts);

        // Remove from localStorage — upload completed
        removePendingMultipart(uploadId!);

        // Step 4: poll for scan result
        updateItem(item.id, { status: "scanning" });
        await pollFileStatus(fileId!, item.id);
      } catch (err: unknown) {
        if (isAbortError(err)) {
          updateItem(item.id, { status: "cancelled" });
        } else {
          const msg = err instanceof Error ? err.message : "Upload failed";
          updateItem(item.id, { status: "error", errorMessage: msg });
        }
      }
    },
    [updateItem, pollFileStatus]
  );

  // ── Route to correct upload strategy ─────────────────────────────────────

  const startUpload = useCallback(
    (item: UploadItem) => {
      if (item.file.size >= RESUMABLE_THRESHOLD) {
        void runMultipartUpload(item);
      } else {
        void runPresignedUpload(item);
      }
    },
    [runPresignedUpload, runMultipartUpload]
  );

  // ── Public API ────────────────────────────────────────────────────────────

  const addFiles = useCallback(
    (files: File[], folderId?: string) => {
      const accepted: File[] = [];

      for (const file of files) {
        const sizeErr = validateFileSize(file);
        if (sizeErr) {
          toast.error(sizeErr);
          continue;
        }
        if (isRiskyFile(file)) {
          toast.warning(
            `⚠️ "${file.name}" may contain malware. The file will be automatically virus-scanned before activation.`,
            { duration: 4000 }
          );
        }
        accepted.push(file);
      }

      const items = accepted.map((file) => createItem(file, folderId));
      setUploadQueue((prev) => [...prev, ...items]);
      items.forEach((item) => startUpload(item));
    },
    [startUpload]
  );

  const cancelUpload = useCallback(
    (itemId: string) => {
      setUploadQueue((prev) => {
        const item = prev.find((i) => i.id === itemId);
        if (!item) return prev;

        // Abort any in-flight fetch/axios
        item.abortController?.abort();

        // If multipart has an active session, abort on backend
        if (item.uploadId) {
          void abortMultipart(item.uploadId).catch(() => {});
          removePendingMultipart(item.uploadId);
        }

        return prev.map((i) =>
          i.id === itemId ? { ...i, status: "cancelled" as UploadStatus } : i
        );
      });
    },
    []
  );

  const retryUpload = useCallback(
    (itemId: string) => {
      setUploadQueue((prev) => {
        const item = prev.find((i) => i.id === itemId);
        if (!item) return prev;

        const fresh: UploadItem = {
          ...item,
          status: "queued",
          progress: 0,
          speedBps: undefined,
          etaSeconds: undefined,
          errorMessage: undefined,
          abortController: undefined,
          // Reset upload state for a clean retry
          fileId: undefined,
          uploadUrl: undefined,
          uploadId: undefined,
          totalParts: undefined,
          uploadedParts: [],
          sessionExpiresAt: undefined,
          presignedExpiresAt: undefined,
        };

        // Start upload outside this setState call
        setTimeout(() => startUpload(fresh), 0);

        return prev.map((i) => (i.id === itemId ? fresh : i));
      });
    },
    [startUpload]
  );

  const resumeUpload = useCallback(
    (itemId: string) => {
      setUploadQueue((prev) => {
        const item = prev.find((i) => i.id === itemId);
        if (!item || !item.uploadId) {
          // Presigned or no session — do a full retry
          return prev;
        }

        // Multipart resume: check session then continue from missing parts
        const uploadId = item.uploadId;
        const currentItem = { ...item, status: "uploading" as UploadStatus, abortController: new AbortController() };

        setTimeout(async () => {
          try {
            const session = await getMultipartSession(uploadId);
            if (session.status === "ABORTED") {
              toast.error("Upload session expired — please re-upload the file.");
              updateItem(itemId, { status: "error", errorMessage: "Session expired" });
              return;
            }
            if (session.status === "COMPLETED") {
              // Session already complete — poll for scan result
              if (currentItem.fileId) {
                updateItem(itemId, { status: "scanning" });
                await pollFileStatus(currentItem.fileId, itemId);
              }
              return;
            }
            // IN_PROGRESS — resume from existing parts
            void runMultipartUpload(
              { ...currentItem, uploadedParts: session.uploadedParts },
              session.uploadedParts
            );
          } catch {
            updateItem(itemId, { status: "error", errorMessage: "Failed to fetch session for resume" });
          }
        }, 0);

        return prev.map((i) => (i.id === itemId ? currentItem : i));
      });
    },
    [updateItem, pollFileStatus, runMultipartUpload]
  );

  const clearDone = useCallback(() => {
    setUploadQueue((prev) =>
      prev.filter((i) => !["done", "cancelled", "infected"].includes(i.status))
    );
  }, []);

  // ── Resume after page refresh (multipart only) ────────────────────────────

  const checkInterruptedUploads = useCallback(
    async (): Promise<PendingMultipartRecord[]> => {
      return getPendingMultiparts();
    },
    []
  );

  const resumeInterruptedUpload = useCallback(
    (record: PendingMultipartRecord, file: File) => {
      // Fetch session from backend to get already-uploaded parts
      void (async () => {
        try {
          const session = await getMultipartSession(record.uploadId);

          if (session.status === "ABORTED") {
            toast.error("Upload session has expired. Please re-upload the file.");
            removePendingMultipart(record.uploadId);
            return;
          }

          const item: UploadItem = {
            id: record.itemId,
            file,
            folderId: record.folderId,
            status: "queued",
            progress: 0,
            uploadId: record.uploadId,
            fileId: record.fileId,
            totalParts: session.totalParts,
            uploadedParts: session.uploadedParts,
          };

          setUploadQueue((prev) => {
            // Avoid duplicates if item already in queue
            if (prev.some((i) => i.id === record.itemId)) return prev;
            return [...prev, item];
          });

          void runMultipartUpload(item, session.uploadedParts);
        } catch {
          toast.error("Failed to resume upload. Please try again.");
        }
      })();
    },
    [runMultipartUpload]
  );

  const discardInterruptedUpload = useCallback((uploadId: string) => {
    removePendingMultipart(uploadId);
    void abortMultipart(uploadId).catch(() => {});
  }, []);

  return {
    uploadQueue,
    addFiles,
    cancelUpload,
    retryUpload,
    resumeUpload,
    clearDone,
    checkInterruptedUploads,
    resumeInterruptedUpload,
    discardInterruptedUpload,
  };
}

// ─── Util ─────────────────────────────────────────────────────────────────────

function isAbortError(err: unknown): boolean {
  return (
    err instanceof Error &&
    (err.name === "AbortError" ||
      err.name === "CanceledError" ||
      err.message === "canceled")
  );
}
