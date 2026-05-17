"use client";

import { createContext, useContext, useEffect, useCallback, useRef } from "react";
import { useSWRConfig } from "swr";
import { useFileUploadInternal } from "@/hooks/use-file-upload";
import { UPLOAD_STATUS } from "@/lib/constants";

// "Active" = anything where closing the tab would interrupt client-side work.
// Notably excludes SCANNING — that runs server-side and isn't affected by tab close.
const TAB_CLOSE_BLOCKING_STATUSES = [
  UPLOAD_STATUS.QUEUED,
  UPLOAD_STATUS.UPLOADING,
  UPLOAD_STATUS.CONFIRMING,
] as const;

const UploadContext = createContext<ReturnType<typeof useFileUploadInternal> | null>(null);

export function UploadProvider({ children }: { children: React.ReactNode }) {
  const { mutate } = useSWRConfig();

  const onUploadDone = useCallback(() => {
    // Invalidate all "items" SWR keys so the files/folders list refreshes automatically
    void mutate((key) => Array.isArray(key) && key[0] === "items");
  }, [mutate]);

  const value = useFileUploadInternal({ onUploadDone });

  // Warn user before closing/refreshing tab while uploads are in progress
  useEffect(() => {
    const hasActive = value.uploadQueue.some((i) =>
      (TAB_CLOSE_BLOCKING_STATUSES as readonly string[]).includes(i.status)
    );
    if (!hasActive) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [value.uploadQueue]);

  // Defensive: close every open SSE scan subscription on provider unmount
  // (e.g. logout, route teardown). Use a ref to always read the latest queue
  // without re-running the effect.
  const queueRef = useRef(value.uploadQueue);
  queueRef.current = value.uploadQueue;
  useEffect(() => {
    return () => {
      queueRef.current.forEach((item) => item.statusSubscription?.close());
    };
  }, []);

  return <UploadContext.Provider value={value}>{children}</UploadContext.Provider>;
}

export function useUpload() {
  const ctx = useContext(UploadContext);
  if (!ctx) throw new Error("useUpload must be used inside <UploadProvider>");
  return ctx;
}
