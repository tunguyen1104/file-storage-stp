import { API_ROUTES, SSE_ERROR_CODE } from "@/lib/constants";
import type {
  FileStatusStreamEvent,
  FileStatusStreamError,
} from "@/types/upload/upload.types";

interface Handlers {
  onStatus: (event: FileStatusStreamEvent) => void;
  onError: (error: FileStatusStreamError) => void;
}

export function subscribeFileStatus(
  fileId: string,
  { onStatus, onError }: Handlers
): { close: () => void } {
  const url = API_ROUTES.FILES.STATUS_STREAM(fileId);
  const es = new EventSource(url, { withCredentials: true });

  const statusHandler = (evt: MessageEvent) => {
    try {
      onStatus(JSON.parse(evt.data) as FileStatusStreamEvent);
    } catch (e) {
      onError({
        code: SSE_ERROR_CODE.STREAM_PARSE_ERROR,
        message: e instanceof Error ? e.message : "Malformed status event",
      });
    }
  };

  const errorHandler = (evt: MessageEvent) => {
    try {
      onError(JSON.parse(evt.data) as FileStatusStreamError);
    } catch {
      onError({ code: SSE_ERROR_CODE.STREAM_PARSE_ERROR, message: "Malformed error event" });
    }
  };

  const pingHandler = () => {
    // heartbeat — liveness only
  };

  es.addEventListener("status", statusHandler);
  es.addEventListener("error", errorHandler);
  es.addEventListener("ping", pingHandler);

  // EventSource.onerror fires for connection-level failures. While CONNECTING the
  // browser is auto-retrying; only treat CLOSED as terminal.
  es.onerror = () => {
    if (es.readyState === EventSource.CLOSED) {
      onError({
        code: SSE_ERROR_CODE.STREAM_LOST,
        message: "Lost connection to scan service",
      });
    }
  };

  let closed = false;
  return {
    close: () => {
      if (closed) return;
      closed = true;
      es.removeEventListener("status", statusHandler);
      es.removeEventListener("error", errorHandler);
      es.removeEventListener("ping", pingHandler);
      es.onerror = null;
      es.close();
    },
  };
}
