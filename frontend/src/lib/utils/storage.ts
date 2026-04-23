import { GB } from "@/lib/constants";

export function formatStorageGb(bytes: number): string {
  if (!bytes) return "—";
  const gb = bytes / GB;
  if (gb >= 1) return `${parseFloat(gb.toFixed(1))} GB`;
  const mb = bytes / (1024 * 1024);
  return `${Math.round(mb)} MB`;
}

export function bytesToGbString(bytes: number): string {
  if (!bytes) return "";
  return parseFloat((bytes / GB).toFixed(4)).toString();
}

export function gbStringToBytes(gb: string): number {
  return Math.round(parseFloat(gb) * GB);
}
