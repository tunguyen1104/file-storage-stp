"use client";

import { useState, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import useSWR from "swr";
import {
  Share2,
  Users,
  Clock,
  FolderOpen,
  FileImage,
  FileText,
  FileArchive,
  FileVideo,
  FileAudio,
  File as FileIcon,
  Folder as FolderIcon,
  MoreVertical,
  Eye,
  Download,
  Trash2,
  ExternalLink,
  Search,
  ShieldCheck,
  ShieldOff,
  User,
  Building,
  Calendar,
  ChevronLeft,
  ChevronRight,
  Sparkles,
  Link,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { toast } from "sonner";
import { useAuth } from "@/hooks/use-auth";
import {
  listSharesWithMe,
  listSharesByMe,
  revokeShare,
  extendShare,
} from "@/lib/api/share";
import { getPresignedGetUrl } from "@/lib/api/file";
import { FilePreviewDialog } from "@/components/files/FilePreviewDialog";
import { ExtendShareDialog } from "@/components/files/ExtendShareDialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

// ─── Helpers ──────────────────────────────────────────────────────

function renderFileIcon(mimeType: string | null, size = "h-5 w-5") {
  if (!mimeType) return <FolderIcon className={cn(size, "text-blue-500 fill-blue-100")} />;
  if (mimeType.startsWith("image/")) return <FileImage className={cn(size, "text-sky-500")} />;
  if (mimeType.startsWith("video/")) return <FileVideo className={cn(size, "text-purple-500")} />;
  if (mimeType.startsWith("audio/")) return <FileAudio className={cn(size, "text-orange-500")} />;
  if (mimeType.includes("zip") || mimeType.includes("tar") || mimeType.includes("rar"))
    return <FileArchive className={cn(size, "text-amber-500")} />;
  if (mimeType.includes("pdf") || mimeType.includes("document") || mimeType.startsWith("text/"))
    return <FileText className={cn(size, "text-red-500")} />;
  return <FileIcon className={cn(size, "text-gray-400")} />;
}

function formatBytes(bytes: number | null): string {
  if (bytes === null || bytes === undefined) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins} phút trước`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs} giờ trước`;
  const days = Math.floor(hrs / 24);
  if (days < 30) return `${days} ngày trước`;
  return new Date(dateStr).toLocaleDateString("vi-VN");
}

const isFilePreviewable = (mimeType: string | null, name: string): boolean => {
  if (!mimeType) {
    const ext = name.split(".").pop()?.toLowerCase();
    const previewableExtensions = [
      "pdf",
      "png",
      "jpg",
      "jpeg",
      "gif",
      "webp",
      "svg",
      "mp4",
      "webm",
      "mp3",
      "ogg",
      "wav",
      "txt",
    ];
    return ext ? previewableExtensions.includes(ext) : false;
  }
  return (
    mimeType === "application/pdf" ||
    mimeType.startsWith("image/") ||
    mimeType.startsWith("video/") ||
    mimeType.startsWith("audio/") ||
    mimeType.startsWith("text/")
  );
};

const getStatusLabel = (status: string) => {
  if (status === "ACTIVE") return "Hoạt động";
  if (status === "EXPIRED") return "Hết hạn";
  if (status === "REVOKED") return "Bị thu hồi";
  return status;
};

// ─── Permission Badge ─────────────────────────────────────────────

function PermissionBadge({ permission }: { permission: "VIEW" | "EDIT" }) {
  return permission === "EDIT" ? (
    <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 border border-emerald-200 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">
      <ShieldCheck className="h-3 w-3" />
      Chỉnh sửa
    </span>
  ) : (
    <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 border border-blue-200 px-2 py-0.5 text-[11px] font-semibold text-blue-600">
      <Eye className="h-3 w-3" />
      Chỉ xem
    </span>
  );
}

// ─── Shared Page Inner ───────────────────────────────────────────

function SharedPageInner() {
  const { user } = useAuth();
  const searchParams = useSearchParams();
  const router = useRouter();
  const tab = (searchParams.get("view") as "received" | "sent") ?? "received";
  const [search, setSearch] = useState("");
  const [resourceTypeFilter, setResourceTypeFilter] = useState<"ALL" | "FILE" | "FOLDER">("ALL");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");

  // Pagination states
  const [receivedPage, setReceivedPage] = useState(1);
  const [sentPage, setSentPage] = useState(1);
  const limit = 12;

  // Selected file preview state
  const [fileToPreview, setFileToPreview] = useState<{
    id: string;
    originalName: string;
    mimeType: string | null;
    sizeBytes: number;
    status: string;
  } | null>(null);

  const [extendShareId, setExtendShareId] = useState<string | null>(null);
  const [extendResourceName, setExtendResourceName] = useState<string>("");

  const isReceived = tab === "received";

  // SWR fetch for Received Shares
  const {
    data: receivedShares,
    mutate: refreshReceived,
    isLoading: isLoadingReceived,
  } = useSWR(
    isReceived
      ? [
        "shares-with-me",
        receivedPage,
        limit,
        resourceTypeFilter === "ALL" ? undefined : resourceTypeFilter.toLowerCase(),
      ]
      : null,
    () =>
      listSharesWithMe({
        page: receivedPage,
        limit,
        resourceType: resourceTypeFilter === "ALL" ? undefined : (resourceTypeFilter.toLowerCase() as any),
      }),
    { revalidateOnFocus: false }
  );

  // SWR fetch for Sent Shares
  const {
    data: sentShares,
    mutate: refreshSent,
    isLoading: isLoadingSent,
  } = useSWR(
    !isReceived
      ? [
        "shares-by-me",
        sentPage,
        limit,
        resourceTypeFilter === "ALL" ? undefined : resourceTypeFilter.toLowerCase(),
        statusFilter === "ALL" ? undefined : statusFilter,
      ]
      : null,
    () =>
      listSharesByMe({
        page: sentPage,
        limit,
        resourceType: resourceTypeFilter === "ALL" ? undefined : (resourceTypeFilter.toLowerCase() as any),
        status: statusFilter === "ALL" ? undefined : statusFilter,
      }),
    { revalidateOnFocus: false }
  );

  // Stats row numbers
  const { data: statsReceived } = useSWR("stats-received-shares", () =>
    listSharesWithMe({ page: 1, limit: 100 })
  );
  const { data: statsSent } = useSWR("stats-sent-shares", () =>
    listSharesByMe({ page: 1, limit: 100 })
  );

  const totalReceivedCount = statsReceived?.totalElements ?? 0;
  const totalSentCount = statsSent?.totalElements ?? 0;
  const totalEditCount = (statsReceived?.data ?? []).filter((s) => s.permission === "EDIT").length;

  const handleDownload = async (fileId: string, originalName: string) => {
    try {
      const { getUrl } = await getPresignedGetUrl(fileId);
      const link = document.createElement("a");
      link.href = getUrl;
      link.setAttribute("download", originalName);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      toast.error("Không thể lấy đường dẫn tải xuống");
    }
  };

  const handleExtend = (shareId: string, resourceName: string) => {
    setExtendShareId(shareId);
    setExtendResourceName(resourceName);
  };

  const handleRevoke = async (shareId: string) => {
    if (!confirm("Bạn có chắc chắn muốn thu hồi quyền chia sẻ này không?")) return;
    try {
      await revokeShare(shareId);
      toast.success("Đã hủy chia sẻ thành công");
      if (isReceived) refreshReceived();
      else refreshSent();
    } catch (err: any) {
      toast.error(err instanceof Error ? err.message : "Hủy chia sẻ thất bại");
    }
  };

  const handleCopyLink = async (fileId: string) => {
    try {
      const { getUrl } = await getPresignedGetUrl(fileId);
      await navigator.clipboard.writeText(getUrl);
      toast.success("Sao chép liên kết thành công!");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Sao chép liên kết thất bại");
    }
  };

  const handlePreviewOrOpen = (item: any) => {
    const isFolder = item.resourceType === "folder" || item.resource?.type === "folder";
    const resId = item.resource?.id || item.fileId || item.folderId;
    const resName = item.resource?.name || item.name;
    const permission: string = item.permission ?? "VIEW";

    if (isFolder) {
      // Redirect to All Files page with sharedPermission param
      router.push(`/files?folderId=${resId}&sharedPermission=${permission}`);
      return;
    }

    // It's a file: preview or download
    const mimeType = item.resource?.mimeType || item.mimeType;
    const sizeBytes = item.resource?.sizeBytes || item.sizeBytes || 0;
    if (isFilePreviewable(mimeType, resName)) {
      setFileToPreview({
        id: resId,
        originalName: resName,
        mimeType: mimeType || null,
        sizeBytes,
        status: "ACTIVE",
      });
    } else {
      toast.info("Tệp tin không được hỗ trợ xem trước, đang tải xuống máy...");
      handleDownload(resId, resName);
    }
  };

  // Filtering list locally using the search query
  const filteredReceived = (receivedShares?.data ?? []).filter((i) =>
    i.resource?.name?.toLowerCase().includes(search.toLowerCase()) ||
    i.sharedBy?.fullName?.toLowerCase().includes(search.toLowerCase())
  );

  const filteredSent = (sentShares?.data ?? []).filter((i) =>
    i.resource?.name?.toLowerCase().includes(search.toLowerCase()) ||
    i.sharedWithUser?.fullName?.toLowerCase().includes(search.toLowerCase()) ||
    i.sharedWithUser?.email?.toLowerCase().includes(search.toLowerCase()) ||
    i.sharedWithTenant?.name?.toLowerCase().includes(search.toLowerCase())
  );

  const totalPages = isReceived
    ? receivedShares?.totalPages ?? 1
    : sentShares?.totalPages ?? 1;

  const currentPage = isReceived ? receivedPage : sentPage;
  const setCurrentPage = isReceived ? setReceivedPage : setSentPage;

  return (
    <div className="flex h-full flex-col overflow-hidden bg-gray-50/70">
      {/* ── Top navigation filter block ── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between border-b border-gray-200 bg-white px-6 py-4 gap-4 shrink-0 shadow-sm z-10">
        <div className="relative w-full sm:max-w-md">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          <Input
            className="h-9 border-gray-200 bg-gray-50 pl-9 text-sm placeholder:text-gray-400 focus-visible:ring-primary/30"
            placeholder="Tìm kiếm theo tên tài nguyên, người gửi..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        {/* Dropdowns */}
        <div className="flex items-center gap-3 self-end sm:self-auto">
          {/* Resource Type */}
          <Select
            value={resourceTypeFilter}
            onValueChange={(v: any) => {
              setResourceTypeFilter(v);
              setCurrentPage(1);
            }}
          >
            <SelectTrigger className="w-[140px] h-9 text-xs font-semibold">
              <SelectValue placeholder="Loại tài nguyên" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Tất cả định dạng</SelectItem>
              <SelectItem value="FILE">Tệp tin (File)</SelectItem>
              <SelectItem value="FOLDER">Thư mục (Folder)</SelectItem>
            </SelectContent>
          </Select>

          {/* Status filter (Only for Sent shares) */}
          {!isReceived && (
            <Select
              value={statusFilter}
              onValueChange={(v) => {
                setStatusFilter(v);
                setCurrentPage(1);
              }}
            >
              <SelectTrigger className="w-[140px] h-9 text-xs font-semibold">
                <SelectValue placeholder="Trạng thái" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Tất cả trạng thái</SelectItem>
                <SelectItem value="ACTIVE">Hoạt động (ACTIVE)</SelectItem>
                <SelectItem value="EXPIRED">Hết hạn (EXPIRED)</SelectItem>
                <SelectItem value="REVOKED">Bị thu hồi (REVOKED)</SelectItem>
              </SelectContent>
            </Select>
          )}
        </div>
      </div>

      {/* ── Scrollable Body ── */}
      <div className="flex-1 overflow-y-auto px-6 py-5 space-y-6 min-h-0 hide-scrollbar">

        {/* Dynamic tabs */}
        <div className="space-y-4">
          <div className="flex items-center gap-1 rounded-xl border border-gray-200 bg-white p-1 shadow-sm w-fit">
            <button
              onClick={() => {
                router.replace("/shared?view=received");
                setCurrentPage(1);
              }}
              className={cn(
                "flex items-center gap-2 rounded-lg px-4 py-2 text-xs font-bold transition-all",
                isReceived
                  ? "bg-primary text-white shadow-sm"
                  : "text-gray-500 hover:text-gray-700"
              )}
            >
              <Share2 className="h-3.5 w-3.5" />
              Được chia sẻ với tôi
              <span
                className={cn(
                  "ml-1 rounded-full px-1.5 py-0.5 text-[10px] font-bold",
                  isReceived ? "bg-white/20 text-white" : "bg-gray-100 text-gray-500"
                )}
              >
                {totalReceivedCount}
              </span>
            </button>
            <button
              onClick={() => {
                router.replace("/shared?view=sent");
                setCurrentPage(1);
              }}
              className={cn(
                "flex items-center gap-2 rounded-lg px-4 py-2 text-xs font-bold transition-all",
                !isReceived
                  ? "bg-primary text-white shadow-sm"
                  : "text-gray-500 hover:text-gray-700"
              )}
            >
              <Users className="h-3.5 w-3.5" />
              Tôi đã chia sẻ
              <span
                className={cn(
                  "ml-1 rounded-full px-1.5 py-0.5 text-[10px] font-bold",
                  !isReceived ? "bg-white/20 text-white" : "bg-gray-100 text-gray-500"
                )}
              >
                {totalSentCount}
              </span>
            </button>
          </div>

          {/* 1. Received Tab View */}
          {isReceived && (
            <>
              {isLoadingReceived ? (
                <div className="space-y-3">
                  {Array.from({ length: 4 }).map((_, i) => (
                    <div key={i} className="h-16 w-full animate-pulse rounded-xl bg-white border border-gray-100" />
                  ))}
                </div>
              ) : filteredReceived.length === 0 ? (
                <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-gray-200 bg-white py-20">
                  <Share2 className="h-10 w-10 text-gray-300 animate-bounce" />
                  <p className="text-sm font-semibold text-gray-400">
                    {search ? `Không tìm thấy kết quả cho "${search}"` : "Chưa có tài nguyên nào được chia sẻ với bạn."}
                  </p>
                </div>
              ) : (
                <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
                  <table className="w-full text-sm table-fixed">
                    <colgroup>
                      <col className="w-[45%]" />
                      <col className="w-[20%]" />
                      <col className="w-[15%]" />
                      <col className="w-[15%]" />
                      <col className="w-[5%]" />
                    </colgroup>
                    <thead className="border-b border-gray-100 bg-gray-50/70">
                      <tr>
                        <th className="px-4 py-3 text-left text-xs font-bold text-gray-500">Tên tài nguyên</th>
                        <th className="px-4 py-3 text-left text-xs font-bold text-gray-500">Chia sẻ bởi</th>
                        <th className="px-4 py-3 text-left text-xs font-bold text-gray-500">Quyền truy cập</th>
                        <th className="px-4 py-3 text-left text-xs font-bold text-gray-500">Thời gian nhận</th>
                        <th className="w-10" />
                      </tr>
                    </thead>
                    <tbody>
                      {filteredReceived.map((item) => {
                        const resName = item.resource?.name ?? "Không rõ tên";
                        const mimeType = item.resource?.mimeType;
                        const isFolder = item.resourceType === "folder";

                        return (
                          <tr
                            key={item.shareId}
                            onClick={() => handlePreviewOrOpen(item)}
                            className="group border-b border-gray-50 transition-colors hover:bg-gray-50/80 cursor-pointer"
                          >
                            <td className="px-4 py-3">
                              <div className="flex items-center gap-2.5 min-w-0">
                                {renderFileIcon(isFolder ? null : mimeType, "h-5 w-5 shrink-0")}
                                <span className="truncate font-semibold text-gray-800" title={resName}>
                                  {resName}
                                </span>
                              </div>
                            </td>
                            <td className="px-4 py-3">
                              <div className="flex items-center gap-2 min-w-0">
                                <Avatar className="h-6 w-6 shrink-0 border border-gray-100">
                                  <AvatarImage src={`https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(item.sharedBy?.fullName || "User")}`} />
                                  <AvatarFallback className="text-[10px] font-bold">{(item.sharedBy?.fullName || "U")[0]}</AvatarFallback>
                                </Avatar>
                                <span className="text-xs font-semibold text-gray-700 truncate">{item.sharedBy?.fullName}</span>
                              </div>
                            </td>
                            <td className="px-4 py-3">
                              <PermissionBadge permission={item.permission} />
                            </td>
                            <td className="px-4 py-3 text-gray-400 text-xs font-medium whitespace-nowrap">
                              {item.expiresAt ? (
                                <span className="flex items-center gap-1">
                                  <Clock className="h-3 w-3 text-amber-500" />
                                  Còn lại: {Math.max(0, Math.ceil((new Date(item.expiresAt).getTime() - Date.now()) / 60000))} phút
                                </span>
                              ) : (
                                "Không giới hạn"
                              )}
                            </td>
                            <td className="px-1 py-3">
                              <DropdownMenu>
                                <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                                  <button className="flex h-8 w-8 items-center justify-center rounded-full text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors opacity-0 group-hover:opacity-100 focus:opacity-100">
                                    <MoreVertical className="h-4 w-4" />
                                  </button>
                                </DropdownMenuTrigger>
                                <DropdownMenuContent align="end" className="w-48">
                                  <DropdownMenuItem
                                    onClick={() => handlePreviewOrOpen(item)}
                                    className="gap-2 text-[12px] font-medium cursor-pointer"
                                  >
                                    <Eye className="h-3.5 w-3.5 text-gray-500" />
                                    {isFolder ? "Mở thư mục" : "Xem tệp tin"}
                                  </DropdownMenuItem>
                                  {!isFolder && (
                                    <>
                                      <DropdownMenuItem
                                        onClick={() => handleCopyLink(item.resource?.id)}
                                        className="gap-2 text-[12px] font-medium cursor-pointer"
                                      >
                                        <Link className="h-3.5 w-3.5 text-gray-500" />
                                        Sao chép liên kết
                                      </DropdownMenuItem>
                                      <DropdownMenuItem
                                        onClick={() => handleDownload(item.resource?.id, resName)}
                                        className="gap-2 text-[12px] font-medium cursor-pointer"
                                      >
                                        <Download className="h-3.5 w-3.5 text-gray-500" />
                                        Tải xuống máy
                                      </DropdownMenuItem>
                                    </>
                                  )}
                                  <DropdownMenuSeparator />
                                  <DropdownMenuItem
                                    onClick={() => handleRevoke(item.shareId)}
                                    className="gap-2 text-[12px] font-medium cursor-pointer text-red-600 focus:text-red-700 focus:bg-red-50"
                                  >
                                    <ShieldOff className="h-3.5 w-3.5" />
                                    Rời khỏi chia sẻ
                                  </DropdownMenuItem>
                                </DropdownMenuContent>
                              </DropdownMenu>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}

          {/* 2. Sent Tab View (Created by Me) */}
          {!isReceived && (
            <>
              {isLoadingSent ? (
                <div className="space-y-3">
                  {Array.from({ length: 4 }).map((_, i) => (
                    <div key={i} className="h-16 w-full animate-pulse rounded-xl bg-white border border-gray-100" />
                  ))}
                </div>
              ) : filteredSent.length === 0 ? (
                <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-gray-200 bg-white py-20">
                  <Users className="h-10 w-10 text-gray-300" />
                  <p className="text-sm font-semibold text-gray-400">
                    {search ? `Không tìm thấy kết quả cho "${search}"` : "Bạn chưa chia sẻ tài nguyên nào."}
                  </p>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {filteredSent.map((item) => {
                    const isFolder = item.resourceType === "folder";
                    const isUserType = item.recipientType === "USER";
                    const labelName = isUserType
                      ? item.sharedWithUser?.fullName || item.sharedWithUser?.email
                      : item.sharedWithTenant?.name || "Tất cả tổ chức";
                    const subLabel = isUserType ? item.sharedWithUser?.email : "Hệ thống / Truy cập tổ chức";

                    const isExpired = item.status === "EXPIRED";
                    const isRevoked = item.status === "REVOKED";

                    return (
                      <div
                        key={item.id}
                        onClick={() => handlePreviewOrOpen(item)}
                        className="group flex items-start gap-3 rounded-xl border border-gray-200 bg-white p-4 hover:border-gray-300 hover:shadow-sm transition-all cursor-pointer relative"
                      >
                        {/* Action Dropdown positioned in top corner */}
                        <div className="absolute right-2 top-2 z-10">
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                              <button className="flex h-7 w-7 items-center justify-center rounded-full text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors opacity-0 group-hover:opacity-100 focus:opacity-100">
                                <MoreVertical className="h-4 w-4" />
                              </button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end" className="w-48">
                              <DropdownMenuItem
                                onClick={() => handlePreviewOrOpen(item)}
                                className="gap-2 text-[12px] font-medium cursor-pointer"
                              >
                                <ExternalLink className="h-3.5 w-3.5 text-gray-500" />
                                Mở chi tiết
                              </DropdownMenuItem>
                              {item.status !== "REVOKED" && (
                                <>
                                  <DropdownMenuItem
                                    onClick={() => handleExtend(item.id, item.resource?.name || "")}
                                    className="gap-2 text-[12px] font-medium cursor-pointer"
                                  >
                                    <Clock className="h-3.5 w-3.5 text-gray-500" />
                                    Gia hạn chia sẻ
                                  </DropdownMenuItem>
                                  <DropdownMenuSeparator />
                                  <DropdownMenuItem
                                    onClick={() => handleRevoke(item.id)}
                                    className="gap-2 text-[12px] font-medium cursor-pointer text-red-600 focus:text-red-700 focus:bg-red-50"
                                  >
                                    <Trash2 className="h-3.5 w-3.5" />
                                    Hủy chia sẻ
                                  </DropdownMenuItem>
                                </>
                              )}
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </div>

                        {/* Icon */}
                        <div className="mt-0.5 shrink-0">
                          {renderFileIcon(isFolder ? null : item.resource?.mimeType, "h-8 w-8")}
                        </div>

                        {/* Text */}
                        <div className="flex-1 min-w-0 pr-6">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <span className="font-bold text-gray-900 text-sm truncate max-w-[200px]" title={item.resource?.name}>
                              {item.resource?.name}
                            </span>
                            <Badge
                              variant="outline"
                              className={cn(
                                "text-[9px] font-bold py-0.5 px-1.5 rounded-full shrink-0",
                                item.status === "ACTIVE"
                                  ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                  : isExpired
                                    ? "bg-amber-50 text-amber-700 border-amber-200"
                                    : "bg-rose-50 text-rose-700 border-rose-200"
                              )}
                            >
                              {getStatusLabel(item.status)}
                            </Badge>
                          </div>

                          <div className="mt-1 flex items-center gap-2.5 text-[11px] font-medium text-gray-400">
                            <span className="flex items-center gap-0.5">
                              <Calendar className="h-3 w-3" />
                              {timeAgo(item.createdAt)}
                            </span>
                            {item.resource?.sizeBytes && <span>{formatBytes(item.resource.sizeBytes)}</span>}
                          </div>

                          {/* Recipient info row */}
                          <div className="mt-3.5 flex items-center gap-2 border-t border-gray-50 pt-2.5">
                            <Avatar className="h-6 w-6 shrink-0">
                              <AvatarImage src={`https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(labelName || "User")}`} />
                              <AvatarFallback className="text-[9px] font-bold">
                                {isUserType ? <User className="h-3 w-3" /> : <Building className="h-3 w-3" />}
                              </AvatarFallback>
                            </Avatar>
                            <div className="min-w-0">
                              <p className="text-[11px] font-bold text-gray-700 truncate leading-none">
                                Gửi cho: {labelName}
                              </p>
                              <p className="text-[10px] text-gray-400 truncate mt-0.5">{subLabel}</p>
                            </div>
                            <div className="ml-auto">
                              <PermissionBadge permission={item.permission} />
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          )}

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-gray-200 bg-white px-4 py-3 sm:px-6 rounded-xl shadow-sm mt-4">
              <div className="flex flex-1 justify-between sm:hidden">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                  disabled={currentPage === 1}
                >
                  Trước
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                  disabled={currentPage === totalPages}
                >
                  Sau
                </Button>
              </div>
              <div className="hidden sm:flex sm:flex-1 sm:items-center sm:justify-between">
                <div>
                  <p className="text-xs text-gray-400 font-bold">
                    Trang <span className="font-extrabold text-gray-700">{currentPage}</span> /{" "}
                    <span className="font-extrabold text-gray-700">{totalPages}</span>
                  </p>
                </div>
                <div className="flex items-center gap-1.5">
                  <Button
                    variant="outline"
                    className="h-8 w-8 p-0"
                    onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                    disabled={currentPage === 1}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
                    <Button
                      key={page}
                      variant={page === currentPage ? "default" : "outline"}
                      className={cn("h-8 w-8 p-0 text-xs font-bold", page === currentPage && "bg-primary text-white")}
                      onClick={() => setCurrentPage(page)}
                    >
                      {page}
                    </Button>
                  ))}
                  <Button
                    variant="outline"
                    className="h-8 w-8 p-0"
                    onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                    disabled={currentPage === totalPages}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </div>
          )}
          </div>
        </div>

      {/* ── File Preview Overlays ── */}
      <FilePreviewDialog
        open={!!fileToPreview}
        onOpenChange={(open) => !open && setFileToPreview(null)}
        file={fileToPreview}
      />
      <ExtendShareDialog
        open={!!extendShareId}
        onOpenChange={(open) => !open && setExtendShareId(null)}
        shareId={extendShareId || ""}
        resourceName={extendResourceName}
        onSuccess={() => {
          if (isReceived) refreshReceived();
          else refreshSent();
        }}
      />
    </div>
  );
}

// ─── Export ───────────────────────────────────────────────────────

export default function SharedPage() {
  return (
    <Suspense
      fallback={
        <div className="flex h-full items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      }
    >
      <SharedPageInner />
    </Suspense>
  );
}
