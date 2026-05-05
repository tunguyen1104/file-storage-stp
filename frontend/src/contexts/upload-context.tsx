"use client";

import { createContext, useContext, useEffect, useCallback } from "react";
import { useSWRConfig } from "swr";
import { useFileUploadInternal } from "@/hooks/use-file-upload";

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
      ["queued", "uploading", "confirming"].includes(i.status)
    );
    if (!hasActive) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [value.uploadQueue]);

  return <UploadContext.Provider value={value}>{children}</UploadContext.Provider>;
}

export function useUpload() {
  const ctx = useContext(UploadContext);
  if (!ctx) throw new Error("useUpload must be used inside <UploadProvider>");
  return ctx;
}
