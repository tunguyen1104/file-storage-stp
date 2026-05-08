export const STORAGE_UNITS = ["B", "KB", "MB", "GB", "TB"] as const;
export type StorageUnit = typeof STORAGE_UNITS[number];

export interface ParsedStorage {
  value: number;
  unit: StorageUnit;
}

const K = 1024;

/**
 * Parses bytes into the most optimal unit and value.
 */
export function parseBytes(bytes: number | null | undefined): ParsedStorage {
  if (bytes === 0 || !bytes) return { value: 0, unit: "B" };
  if (bytes < 0) return { value: 0, unit: "B" };

  const i = Math.floor(Math.log(bytes) / Math.log(K));
  const unitIndex = Math.min(Math.max(i, 0), STORAGE_UNITS.length - 1);
  const unit = STORAGE_UNITS[unitIndex];

  let value = bytes / Math.pow(K, unitIndex);
  
  // Format to a maximum of 2 decimal places to avoid floating point issues
  value = parseFloat(value.toFixed(2));

  return { value, unit };
}

/**
 * Formats bytes to a string like "512.4 GB".
 */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === 0 || !bytes) return "0 B";
  const { value, unit } = parseBytes(bytes);
  return `${value} ${unit}`;
}

/**
 * Converts a value + unit (e.g. 10, "GB") back into raw Bytes.
 */
export function convertToBytes(value: number, unit: StorageUnit): number {
  if (!value) return 0;
  const index = STORAGE_UNITS.indexOf(unit);
  if (index === -1) return value;
  
  return Math.round(value * Math.pow(K, index));
}
