import type { PendingMultipartRecord } from "@/types/upload/upload.types";

const STORAGE_KEY = "upload:pending-multipart";

/**
 * Upsert a record into localStorage.
 * Dedup key = uploadId (MinIO session ID, globally unique).
 * Multiple files with the same name are stored as separate records — no overwrites.
 */
export function savePendingMultipart(record: PendingMultipartRecord): void {
  const existing = getPendingMultiparts();
  // Filter by uploadId — NOT by fileName to avoid accidentally removing other same-name sessions
  const updated = [...existing.filter((r) => r.uploadId !== record.uploadId), record];
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  } catch {
    // localStorage may be full or unavailable — fail silently
  }
}

/**
 * Remove a single pending multipart record by its uploadId.
 * Called after successful complete or user-triggered abort.
 */
export function removePendingMultipart(uploadId: string): void {
  const updated = getPendingMultiparts().filter((r) => r.uploadId !== uploadId);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  } catch {
    // fail silently
  }
}

/**
 * Get all pending multipart records that are still within the 24-hour session TTL.
 * Records older than 24 hours are automatically filtered out (backend session expired).
 */
export function getPendingMultiparts(): PendingMultipartRecord[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const all: PendingMultipartRecord[] = JSON.parse(raw);
    // Filter out records older than 24h (backend session has expired)
    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    return all.filter((r) => new Date(r.startedAt).getTime() > cutoff);
  } catch {
    return [];
  }
}
