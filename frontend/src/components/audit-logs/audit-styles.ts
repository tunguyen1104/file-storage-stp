import type { ActorRole, AuditSeverity } from "@/types/audit/audit";

export const SEVERITY_STYLES: Record<
  AuditSeverity,
  {
    label: string;
    chip: string;
    dot: string;
    row: string;
    icon: string;
    card: string;
    cardIcon: string;
    cardText: string;
  }
> = {
  ERROR: {
    label: "Lỗi",
    chip: "bg-red-50 text-red-700 border-red-200",
    dot: "bg-red-500",
    row: "before:bg-red-500",
    icon: "text-red-500",
    card: "border-red-200 bg-red-50/60",
    cardIcon: "bg-red-100 text-red-600",
    cardText: "text-red-700",
  },
  WARNING: {
    label: "Cảnh báo",
    chip: "bg-amber-50 text-amber-700 border-amber-200",
    dot: "bg-amber-500",
    row: "before:bg-amber-500",
    icon: "text-amber-500",
    card: "border-amber-200 bg-amber-50/60",
    cardIcon: "bg-amber-100 text-amber-600",
    cardText: "text-amber-700",
  },
  INFO: {
    label: "Thông tin",
    chip: "bg-sky-50 text-sky-700 border-sky-200",
    dot: "bg-sky-500",
    row: "before:bg-sky-500",
    icon: "text-sky-500",
    card: "border-sky-200 bg-sky-50/60",
    cardIcon: "bg-sky-100 text-sky-600",
    cardText: "text-sky-700",
  },
  SUCCESS: {
    label: "Thành công",
    chip: "bg-emerald-50 text-emerald-700 border-emerald-200",
    dot: "bg-emerald-500",
    row: "before:bg-emerald-500",
    icon: "text-emerald-500",
    card: "border-emerald-200 bg-emerald-50/60",
    cardIcon: "bg-emerald-100 text-emerald-600",
    cardText: "text-emerald-700",
  },
};

export const ACTOR_ROLE_LABELS: Record<ActorRole, string> = {
  SUPER_ADMIN: "Quản trị hệ thống",
  TENANT_ADMIN: "Quản trị tổ chức",
  MEMBER: "Thành viên",
  SYSTEM: "Hệ thống",
  ANONYMOUS: "Ẩn danh",
};

/**
 * Color for the small role chip rendered beneath the actor email.
 * SUPER_ADMIN → red, TENANT_ADMIN → amber, MEMBER/SYSTEM/ANONYMOUS → gray.
 */
export function actorRoleChipClass(role: ActorRole | null | undefined): string {
  if (role === "SUPER_ADMIN") {
    return "bg-red-50 text-red-700 border-red-200";
  }
  if (role === "TENANT_ADMIN") {
    return "bg-amber-50 text-amber-700 border-amber-200";
  }
  return "bg-gray-100 text-gray-600 border-gray-200";
}
