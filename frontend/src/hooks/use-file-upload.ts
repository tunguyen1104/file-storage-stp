"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { toast } from "sonner";
import {
  willBeVirusScanned,
  validateFileSize,
  RESUMABLE_THRESHOLD,
  FILE_STATUS,
  UPLOAD_STATUS,
  SSE_ERROR_CODE,
  MULTIPART_SESSION_STATUS,
  UPLOAD_MESSAGES,
  UPLOAD_FAILURE_STATUSES,
} from "@/lib/constants";
import {
  initPresignedUpload,
  confirmUpload,
  subscribeFileStatus,
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
} from "@/lib/utils/upload-utils";
import type { UploadItem, PartInfo, PendingMultipartRecord } from "@/types/upload/upload.types";

// Maximum concurrent chunk uploads per multipart file
const MULTIPART_CONCURRENCY = 3;

// ─── Helpers ─────────────────────────────────────────────────────────────────

function createItem(file: File, folderId?: string): UploadItem {
  return {
    id: crypto.randomUUID(),
    file,
    folderId,
    status: UPLOAD_STATUS.QUEUED,
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
  const [interruptedUploads, setInterruptedUploads] = useState<PendingMultipartRecord[]>([]);

  const onUploadDoneRef = useRef(options?.onUploadDone);
  useEffect(() => { onUploadDoneRef.current = options?.onUploadDone; }, [options?.onUploadDone]);

  // Load pending multipart records from localStorage on mount.
  // Runs client-side only — restores state regardless of which page the user is on after a reload.
  useEffect(() => {
    setInterruptedUploads(getPendingMultiparts());
  }, []);

  // ── Queue helpers ─────────────────────────────────────────────────────────

  const updateItem = useCallback(
    (itemId: string, patch: Partial<UploadItem>) => {
      setUploadQueue((prev) =>
        prev.map((item) => (item.id === itemId ? { ...item, ...patch } : item))
      );
    },
    []
  );

  // ── Status subscription (SSE) ─────────────────────────────────────────────

  /**
   * Open an SSE subscription to receive terminal status (ACTIVE / INFECTED /
   * UPLOAD_FAILED) once the backend finishes virus scanning. Replaces the prior
   * 2s polling loop. Backend emits SCAN_TIMEOUT on its 4-minute deadline.
   */
  const runStatusSubscription = useCallback(
    (fileId: string, itemId: string) => {
      const sub = subscribeFileStatus(fileId, {
        onStatus: (payload) => {
          if (payload.status === FILE_STATUS.ACTIVE) {
            updateItem(itemId, {
              status: UPLOAD_STATUS.DONE,
              progress: 100,
              statusSubscription: undefined,
            });
            onUploadDoneRef.current?.();
            sub.close();
          } else if (payload.status === FILE_STATUS.INFECTED) {
            updateItem(itemId, {
              status: UPLOAD_STATUS.INFECTED,
              errorMessage: UPLOAD_MESSAGES.VIRUS_DETECTED,
              statusSubscription: undefined,
            });
            sub.close();
          } else if (payload.status === FILE_STATUS.UPLOAD_FAILED) {
            updateItem(itemId, {
              status: UPLOAD_STATUS.ERROR,
              errorMessage: UPLOAD_MESSAGES.PROCESSING_FAILED,
              statusSubscription: undefined,
            });
            sub.close();
          }
          // SCANNING — server-pushed initial state; keep listening
        },
        onError: (err) => {
          const message =
            err.code === SSE_ERROR_CODE.SCAN_TIMEOUT
              ? UPLOAD_MESSAGES.SCAN_TIMEOUT
              : err.code === SSE_ERROR_CODE.STREAM_LOST
                ? UPLOAD_MESSAGES.SCAN_CONNECTION_LOST
                : err.message;
          updateItem(itemId, {
            status: UPLOAD_STATUS.ERROR,
            errorMessage: message,
            statusSubscription: undefined,
          });
          sub.close();
        },
      });
      updateItem(itemId, { statusSubscription: sub });
    },
    [updateItem]
  );

  // ── Presigned upload (file < RESUMABLE_THRESHOLD) ─────────────────────────

  const runPresignedUpload = useCallback(
    async (item: UploadItem) => {
      const abortController = new AbortController();
      updateItem(item.id, { status: UPLOAD_STATUS.UPLOADING, progress: 0, abortController });

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
        updateItem(item.id, { status: UPLOAD_STATUS.CONFIRMING, progress: 100 });
        const confirmResult = await confirmUpload(fileId);

        // Step 4: branch on backend-reported status — scan bypass returns ACTIVE immediately
        if (confirmResult.status === FILE_STATUS.ACTIVE) {
          updateItem(item.id, { status: UPLOAD_STATUS.DONE, progress: 100 });
          onUploadDoneRef.current?.();
        } else if (confirmResult.status === FILE_STATUS.INFECTED) {
          updateItem(item.id, {
            status: UPLOAD_STATUS.INFECTED,
            errorMessage: UPLOAD_MESSAGES.VIRUS_DETECTED,
          });
        } else if (confirmResult.status === FILE_STATUS.UPLOAD_FAILED) {
          updateItem(item.id, {
            status: UPLOAD_STATUS.ERROR,
            errorMessage: UPLOAD_MESSAGES.PROCESSING_FAILED,
          });
        } else {
          if (willBeVirusScanned(item.file)) {
            updateItem(item.id, { status: UPLOAD_STATUS.SCANNING });
          }
          runStatusSubscription(fileId, item.id);
        }
      } catch (err: unknown) {
        if (isAbortError(err)) {
          updateItem(item.id, { status: UPLOAD_STATUS.CANCELLED });
        } else {
          const msg = err instanceof Error ? err.message : UPLOAD_MESSAGES.GENERIC_UPLOAD_FAILED;
          updateItem(item.id, { status: UPLOAD_STATUS.ERROR, errorMessage: msg });
        }
      }
    },
    [updateItem, runStatusSubscription]
  );

  // ── Multipart upload (file >= RESUMABLE_THRESHOLD) ────────────────────────

  const runMultipartUpload = useCallback(
    async (item: UploadItem, resumeFromParts?: PartInfo[]) => {
      const abortController = new AbortController();
      updateItem(item.id, { status: UPLOAD_STATUS.UPLOADING, abortController });

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
          updateItem(item.id, { status: UPLOAD_STATUS.CANCELLED });
          return;
        }

        // Step 3: complete multipart — all parts must be uploaded
        const sortedParts = buildPartList(completedParts);
        updateItem(item.id, { status: UPLOAD_STATUS.CONFIRMING, progress: 100 });
        const completeResult = await completeMultipart(uploadId!, sortedParts);

        // Remove from localStorage — upload completed
        removePendingMultipart(uploadId!);

        // Step 4: branch on backend-reported status — scan bypass returns ACTIVE immediately
        if (completeResult.status === FILE_STATUS.ACTIVE) {
          updateItem(item.id, { status: UPLOAD_STATUS.DONE, progress: 100 });
          onUploadDoneRef.current?.();
        } else if (completeResult.status === FILE_STATUS.INFECTED) {
          updateItem(item.id, {
            status: UPLOAD_STATUS.INFECTED,
            errorMessage: UPLOAD_MESSAGES.VIRUS_DETECTED,
          });
        } else if (completeResult.status === FILE_STATUS.UPLOAD_FAILED) {
          updateItem(item.id, {
            status: UPLOAD_STATUS.ERROR,
            errorMessage: UPLOAD_MESSAGES.PROCESSING_FAILED,
          });
        } else {
          if (willBeVirusScanned(item.file)) {
            updateItem(item.id, { status: UPLOAD_STATUS.SCANNING });
          }
          runStatusSubscription(fileId!, item.id);
        }
      } catch (err: unknown) {
        if (isAbortError(err)) {
          updateItem(item.id, { status: UPLOAD_STATUS.CANCELLED });
        } else {
          const msg = err instanceof Error ? err.message : UPLOAD_MESSAGES.GENERIC_UPLOAD_FAILED;
          updateItem(item.id, { status: UPLOAD_STATUS.ERROR, errorMessage: msg });
        }
      }
    },
    [updateItem, runStatusSubscription]
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
        if (!willBeVirusScanned(file)) {
          toast.warning(
            `⚠️ "${file.name}" will not be virus-scanned (file type or size exceeds scan limits). Upload will proceed without security scanning.`,
            { duration: 5000, position: "top-right" }
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

        // Close any open SSE scan subscription
        item.statusSubscription?.close();

        // If multipart has an active session, abort on backend
        if (item.uploadId) {
          void abortMultipart(item.uploadId).catch(() => {});
          removePendingMultipart(item.uploadId);
        }

        return prev.map((i) =>
          i.id === itemId
            ? { ...i, status: UPLOAD_STATUS.CANCELLED, statusSubscription: undefined }
            : i
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

        // Close any open SSE scan subscription from the prior attempt
        item.statusSubscription?.close();

        const fresh: UploadItem = {
          ...item,
          status: UPLOAD_STATUS.QUEUED,
          progress: 0,
          speedBps: undefined,
          etaSeconds: undefined,
          errorMessage: undefined,
          abortController: undefined,
          statusSubscription: undefined,
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
        const currentItem = {
          ...item,
          status: UPLOAD_STATUS.UPLOADING,
          abortController: new AbortController(),
        };

        setTimeout(async () => {
          try {
            const session = await getMultipartSession(uploadId);
            if (session.status === MULTIPART_SESSION_STATUS.ABORTED) {
              toast.error("Upload session expired — please re-upload the file.");
              updateItem(itemId, { status: UPLOAD_STATUS.ERROR, errorMessage: "Session expired" });
              return;
            }
            if (session.status === MULTIPART_SESSION_STATUS.COMPLETED) {
              // Session already complete — subscribe for the scan result
              if (currentItem.fileId) {
                updateItem(itemId, { status: UPLOAD_STATUS.SCANNING });
                runStatusSubscription(currentItem.fileId, itemId);
              }
              return;
            }
            // IN_PROGRESS — resume from existing parts
            void runMultipartUpload(
              { ...currentItem, uploadedParts: session.uploadedParts },
              session.uploadedParts
            );
          } catch {
            updateItem(itemId, {
              status: UPLOAD_STATUS.ERROR,
              errorMessage: "Failed to fetch session for resume",
            });
          }
        }, 0);

        return prev.map((i) => (i.id === itemId ? currentItem : i));
      });
    },
    [updateItem, runStatusSubscription, runMultipartUpload]
  );

  const clearDone = useCallback(() => {
    setUploadQueue((prev) => prev.filter((i) => i.status !== UPLOAD_STATUS.DONE));
  }, []);

  const clearErrors = useCallback(() => {
    setUploadQueue((prev) =>
      prev.filter((i) => !UPLOAD_FAILURE_STATUSES.includes(i.status))
    );
  }, []);

  const resumeInterruptedUpload = useCallback(
    (record: PendingMultipartRecord, file: File) => {
      // Remove from interrupted state immediately so the banner/widget dismisses
      setInterruptedUploads((prev) => prev.filter((r) => r.uploadId !== record.uploadId));

      void (async () => {
        try {
          const session = await getMultipartSession(record.uploadId);

          if (session.status === MULTIPART_SESSION_STATUS.ABORTED) {
            toast.error("Upload session has expired. Please re-upload the file.");
            removePendingMultipart(record.uploadId);
            return;
          }

          const item: UploadItem = {
            id: record.itemId,
            file,
            folderId: record.folderId,
            status: UPLOAD_STATUS.QUEUED,
            progress: 0,
            uploadId: record.uploadId,
            fileId: record.fileId,
            totalParts: session.totalParts,
            uploadedParts: session.uploadedParts,
          };

          setUploadQueue((prev) => {
            if (prev.some((i) => i.id === record.itemId)) return prev;
            return [...prev, item];
          });

          void runMultipartUpload(item, session.uploadedParts);
        } catch {
          toast.error("Failed to resume upload. Please try again.");
          // Restore record so user can retry
          setInterruptedUploads((prev) =>
            prev.some((r) => r.uploadId === record.uploadId) ? prev : [...prev, record]
          );
        }
      })();
    },
    [runMultipartUpload]
  );

  const discardInterruptedUpload = useCallback((uploadId: string) => {
    setInterruptedUploads((prev) => prev.filter((r) => r.uploadId !== uploadId));
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
    clearErrors,
    interruptedUploads,
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
