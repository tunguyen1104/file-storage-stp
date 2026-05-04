import { CHUNK_SIZE } from "@/lib/constants";
import type { PartInfo } from "@/types/upload/upload.types";

/** Format bytes to human-readable string */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

/** Format speed to human-readable string (e.g. "1.2 MB/s") */
export function formatSpeed(bytesPerSec: number): string {
  if (bytesPerSec < 1024) return `${Math.round(bytesPerSec)} B/s`;
  if (bytesPerSec < 1024 * 1024) return `${(bytesPerSec / 1024).toFixed(1)} KB/s`;
  return `${(bytesPerSec / (1024 * 1024)).toFixed(1)} MB/s`;
}

/** Format seconds to human-readable ETA string (e.g. "2m 30s", "45s") */
export function formatEta(seconds: number): string {
  if (seconds < 60) return `${Math.round(seconds)}s`;
  const mins = Math.floor(seconds / 60);
  const secs = Math.round(seconds % 60);
  return secs > 0 ? `${mins}m ${secs}s` : `${mins}m`;
}

/**
 * Calculate the number of chunks for a given file size.
 * Last chunk may be smaller than CHUNK_SIZE.
 */
export function calcTotalParts(fileSize: number): number {
  return Math.ceil(fileSize / CHUNK_SIZE);
}

/**
 * Slice a File into a specific part (1-indexed).
 * Returns a Blob representing the chunk bytes.
 */
export function slicePart(file: File, partNumber: number): Blob {
  const start = (partNumber - 1) * CHUNK_SIZE;
  const end = Math.min(start + CHUNK_SIZE, file.size);
  return file.slice(start, end);
}

/**
 * Build a sorted parts list from completed part infos.
 * Ensures parts are in ascending order by partNumber as required by MinIO.
 */
export function buildPartList(parts: PartInfo[]): PartInfo[] {
  return [...parts].sort((a, b) => a.partNumber - b.partNumber);
}

/**
 * Determine which part numbers are still pending (not yet uploaded).
 * Used for resume: given totalParts and the set of completed partNumbers,
 * return the missing part numbers in ascending order.
 */
export function getMissingParts(
  totalParts: number,
  uploadedParts: PartInfo[]
): number[] {
  const done = new Set(uploadedParts.map((p) => p.partNumber));
  const missing: number[] = [];
  for (let i = 1; i <= totalParts; i++) {
    if (!done.has(i)) missing.push(i);
  }
  return missing;
}

/** Simple promise-based sleep */
export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
