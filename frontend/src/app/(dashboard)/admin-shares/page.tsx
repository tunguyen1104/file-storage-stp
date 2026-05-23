"use client";

import { useState, useEffect, Suspense } from "react";
import { useRouter } from "next/navigation";
import useSWR from "swr";
import {
  Share2,
  Users,
  Clock,
  FolderOpen,
  File as FileIcon,
  Folder as FolderIcon,
  Search,
  ShieldCheck,
  Eye,
  Building,
  Calendar,
  ChevronLeft,
  ChevronRight,
  ArrowUp,
  ArrowDown,
  Copy,
  User,
  Loader2,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { toast } from "sonner";
import { useAuth } from "@/hooks/use-auth";
import { adminListShares, adminRevokeShare } from "@/lib/api/share";
import { listTenants } from "@/lib/api/tenants";
import { listTenantUsers } from "@/lib/api/users";
import { formatBytes } from "@/lib/utils/storage";

function PermissionBadge({ permission }: { permission: "VIEW" | "EDIT" }) {
  return permission === "EDIT" ? (
    <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 border border-emerald-200 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-700">
      <ShieldCheck className="h-3 w-3" />
      Chỉnh sửa
    </span>
  ) : (
    <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 border border-blue-200 px-2.5 py-0.5 text-[11px] font-semibold text-blue-600">
      <Eye className="h-3 w-3" />
      Chỉ xem
    </span>
  );
}

const getStatusBadge = (status: string) => {
  switch (status) {
    case "ACTIVE":
      return (
        <Badge variant="outline" className="bg-emerald-50 text-emerald-700 border-emerald-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          Hoạt động
        </Badge>
      );
    case "EXPIRED":
      return (
        <Badge variant="outline" className="bg-amber-50 text-amber-700 border-amber-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          Hết hạn
        </Badge>
      );
    case "REVOKED":
      return (
        <Badge variant="outline" className="bg-rose-50 text-rose-700 border-rose-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          Đã thu hồi
        </Badge>
      );
    default:
      return (
        <Badge variant="outline" className="bg-gray-50 text-gray-500 border-gray-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          {status}
        </Badge>
      );
  }
};

function AdminSharesPageInner() {
  const { user, isLoading: isAuthLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isAuthLoading && !user) {
      router.push("/login");
    } else if (!isAuthLoading && user && user.role !== "SUPER_ADMIN" && user.role !== "TENANT_ADMIN") {
      router.push("/unauthorized");
    }
  }, [user, isAuthLoading, router]);

  const [currentPage, setCurrentPage] = useState(1);
  const [resourceTypeFilter, setResourceTypeFilter] = useState<string>("all");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [selectedTenantId, setSelectedTenantId] = useState<string>("all");
  const [selectedSharedById, setSelectedSharedById] = useState<string>("all");

  const [sortBy, setSortBy] = useState<string>("CREATED_AT");
  const [sortDirection, setSortDirection] = useState<"ASC" | "DESC">("DESC");

  const isSuperAdmin = user?.role === "SUPER_ADMIN";
  const isTenantAdmin = user?.role === "TENANT_ADMIN";

  // Fetch tenants for Super Admin filter
  const { data: tenantsData } = useSWR(
    isSuperAdmin ? "admin-shares-tenants-list" : null,
    () => listTenants({ page: 1, limit: 100 }),
    { revalidateOnFocus: false }
  );
  const tenantsList = tenantsData?.data ?? [];

  // Fetch tenant users for Tenant Admin filter
  const { data: usersData } = useSWR(
    isTenantAdmin ? "admin-shares-users-list" : null,
    () => listTenantUsers({ limit: 100 }),
    { revalidateOnFocus: false }
  );
  const usersList = usersData?.data ?? [];

  // Fetch shares SWR Key
  const swrKey = [
    "admin-shares-list",
    currentPage,
    resourceTypeFilter,
    statusFilter,
    selectedTenantId,
    selectedSharedById,
    sortBy,
    sortDirection,
    user?.id,
  ] as const;

  const { data, isLoading, mutate } = useSWR(
    user ? swrKey : null,
    async ([, page, rType, status, tId, sById, sBy, sDir]) => {
      const params: any = {
        page,
        limit: 10,
        resourceType: rType === "all" ? undefined : (rType as any),
        status: status === "all" ? undefined : [status],
        sortBy: [sBy],
        sortDirection: [sDir],
      };

      if (isSuperAdmin && tId !== "all") {
        params.tenantId = [tId];
      }
      if (isTenantAdmin && sById !== "all") {
        params.sharedBy = [sById];
      }

      return adminListShares(params);
    },
    { revalidateOnFocus: false }
  );

  const shares = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;

  const handleSort = (field: string) => {
    if (sortBy === field) {
      setSortDirection(sortDirection === "ASC" ? "DESC" : "ASC");
    } else {
      setSortBy(field);
      setSortDirection(defaultDirection(field));
    }
    setCurrentPage(1);
  };

  const defaultDirection = (field: string) => {
    switch (field) {
      case "CREATED_AT":
      case "EXPIRES_AT":
      case "REVOKED_AT":
        return "DESC";
      case "STATUS":
      case "TENANT_ID":
        return "ASC";
      default:
        return "DESC";
    }
  };

  const SortIcon = ({ field }: { field: string }) => {
    if (sortBy !== field) return null;
    return sortDirection === "ASC" ? <ArrowUp className="w-3.5 h-3.5 ml-1 inline" /> : <ArrowDown className="w-3.5 h-3.5 ml-1 inline" />;
  };

  const [revokeTarget, setRevokeTarget] = useState<any | null>(null);
  const [isRevoking, setIsRevoking] = useState(false);

  const handleRevoke = (item: any) => {
    setRevokeTarget(item);
  };

  const handleConfirmRevoke = async () => {
    if (!revokeTarget) return;
    setIsRevoking(true);
    try {
      await adminRevokeShare(revokeTarget.id);
      toast.success("Thu hồi quyền chia sẻ thành công");
      mutate();
      setRevokeTarget(null);
    } catch (err: any) {
      toast.error(err instanceof Error ? err.message : "Thu hồi quyền chia sẻ thất bại");
    } finally {
      setIsRevoking(false);
    }
  };

  const handleCopy = async (text: string, message: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(message);
    } catch {
      toast.error("Không thể sao chép");
    }
  };

  if (isAuthLoading || !user) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="bg-gray-50 min-h-[calc(100vh-2rem)] p-4 md:p-8">
      {/* Top Search & Actions */}
      <div className="flex flex-col md:flex-row gap-4 justify-between items-center mb-8">
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-purple-100 text-purple-600 shadow-sm border border-purple-200">
            <Share2 className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-900 leading-tight">Quản lý chia sẻ</h1>
            <p className="text-sm text-gray-500 mt-1">
              {isSuperAdmin
                ? "Quản lý toàn bộ liên kết chia sẻ của các tổ chức trong hệ thống"
                : "Quản lý liên kết chia sẻ của các thành viên trong tổ chức"}
            </p>
          </div>
        </div>
      </div>

      {/* Main Content Box */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
        {/* Header with Filters */}
        <div className="px-6 py-5 border-b border-gray-100 flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div>
            <h2 className="text-base font-bold text-gray-900 leading-tight">Danh sách liên kết</h2>
            <p className="text-xs text-gray-500 mt-1">Sử dụng bộ lọc bên phải để tìm kiếm liên kết cụ thể.</p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            {/* Super Admin Tenant Filter */}
            {isSuperAdmin && (
              <div className="w-[180px]">
                <Select
                  value={selectedTenantId}
                  onValueChange={(v) => {
                    setSelectedTenantId(v);
                    setCurrentPage(1);
                  }}
                >
                  <SelectTrigger className="h-10 border-gray-200">
                    <SelectValue placeholder="Lọc theo Tổ chức" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">Tất cả tổ chức</SelectItem>
                    {tenantsList.map((t) => (
                      <SelectItem key={t.id} value={t.id}>
                        <div className="truncate max-w-[140px]" title={t.name}>
                          {t.name}
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            {/* Tenant Admin Creator Filter */}
            {isTenantAdmin && (
              <div className="w-[180px]">
                <Select
                  value={selectedSharedById}
                  onValueChange={(v) => {
                    setSelectedSharedById(v);
                    setCurrentPage(1);
                  }}
                >
                  <SelectTrigger className="h-10 border-gray-200">
                    <SelectValue placeholder="Lọc theo Người tạo" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">Tất cả người tạo</SelectItem>
                    {usersList.map((u) => (
                      <SelectItem key={u.id} value={u.id}>
                        <div className="truncate max-w-[140px]" title={u.fullName || u.email}>
                          {u.fullName || u.email}
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            {/* Resource Type Filter */}
            <div className="w-[150px]">
              <Select
                value={resourceTypeFilter}
                onValueChange={(v) => {
                  setResourceTypeFilter(v);
                  setCurrentPage(1);
                }}
              >
                <SelectTrigger className="h-10 border-gray-200">
                  <SelectValue placeholder="Loại tài nguyên" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">Tất cả loại</SelectItem>
                  <SelectItem value="file">Tệp tin</SelectItem>
                  <SelectItem value="folder">Thư mục</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* Status Filter */}
            <div className="w-[150px]">
              <Select
                value={statusFilter}
                onValueChange={(v) => {
                  setStatusFilter(v);
                  setCurrentPage(1);
                }}
              >
                <SelectTrigger className="h-10 border-gray-200">
                  <SelectValue placeholder="Trạng thái" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">Tất cả trạng thái</SelectItem>
                  <SelectItem value="ACTIVE">Hoạt động</SelectItem>
                  <SelectItem value="EXPIRED">Hết hạn</SelectItem>
                  <SelectItem value="REVOKED">Đã thu hồi</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          <Table className="min-w-[1000px]">
            <TableHeader>
              <TableRow className="hover:bg-transparent border-b border-gray-100">
                <TableHead className="font-semibold text-gray-700 py-4 h-auto px-6">Tài nguyên</TableHead>
                {isSuperAdmin && (
                  <TableHead
                    className={cn(
                      "font-semibold py-4 h-auto cursor-pointer select-none transition-colors",
                      sortBy === "TENANT_ID" ? "text-purple-600" : "text-gray-700"
                    )}
                    onClick={() => handleSort("TENANT_ID")}
                  >
                    Tổ chức <SortIcon field="TENANT_ID" />
                  </TableHead>
                )}
                <TableHead className="font-semibold text-gray-700 py-4 h-auto">Người chia sẻ</TableHead>
                <TableHead className="font-semibold text-gray-700 py-4 h-auto">Chia sẻ với</TableHead>
                <TableHead className="font-semibold text-gray-700 py-4 h-auto">Quyền</TableHead>
                <TableHead
                  className={cn(
                    "font-semibold py-4 h-auto cursor-pointer select-none transition-colors",
                    sortBy === "CREATED_AT" ? "text-purple-600" : "text-gray-700"
                  )}
                  onClick={() => handleSort("CREATED_AT")}
                >
                  Ngày tạo <SortIcon field="CREATED_AT" />
                </TableHead>
                <TableHead
                  className={cn(
                    "font-semibold py-4 h-auto cursor-pointer select-none transition-colors",
                    sortBy === "EXPIRES_AT" ? "text-purple-600" : "text-gray-700"
                  )}
                  onClick={() => handleSort("EXPIRES_AT")}
                >
                  Hạn dùng <SortIcon field="EXPIRES_AT" />
                </TableHead>
                <TableHead
                  className={cn(
                    "font-semibold py-4 h-auto text-center cursor-pointer select-none transition-colors",
                    sortBy === "STATUS" ? "text-purple-600" : "text-gray-700"
                  )}
                  onClick={() => handleSort("STATUS")}
                >
                  Trạng thái <SortIcon field="STATUS" />
                </TableHead>
                <TableHead className="font-semibold text-gray-700 py-4 h-auto text-center">Thao tác</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={isSuperAdmin ? 9 : 8} className="h-32 text-center text-gray-500">
                    Đang tải danh sách chia sẻ...
                  </TableCell>
                </TableRow>
              ) : shares.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={isSuperAdmin ? 9 : 8} className="h-32 text-center text-gray-500">
                    Không tìm thấy liên kết chia sẻ nào.
                  </TableCell>
                </TableRow>
              ) : (
                shares.map((item) => {
                  const isFolder = item.resourceType === "folder";
                  const isUserType = item.recipientType === "USER";
                  const recipientLabel = isUserType
                    ? item.sharedWithUser?.fullName || item.sharedWithUser?.email || "—"
                    : item.sharedWithTenant?.name || "Tất cả tổ chức";
                  const recipientSub = isUserType ? item.sharedWithUser?.email : "Chia sẻ cấp tổ chức";

                  const resId = isFolder ? item.folderId : item.fileId;

                  return (
                    <TableRow key={item.id} className="hover:bg-gray-50/50 border-b border-gray-100">
                      <TableCell className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <div className="shrink-0">
                            {isFolder ? (
                              <FolderIcon className="w-8 h-8 text-blue-500 fill-blue-50" />
                            ) : (
                              <FileIcon className="w-8 h-8 text-purple-500 fill-purple-50" />
                            )}
                          </div>
                          <div className="flex flex-col max-w-[200px]">
                            <span className="font-bold text-gray-900 text-sm leading-tight truncate" title={item.resourceName || ""}>
                              {item.resourceName || "—"}
                            </span>
                          </div>
                        </div>
                      </TableCell>

                      {isSuperAdmin && (
                        <TableCell className="py-4">
                          <div className="max-w-[250px] truncate" title={item?.tenantName || ""}>
                            <span className="text-sm font-semibold text-gray-700">
                              {item?.tenantName || "—"}
                            </span>
                          </div>
                        </TableCell>
                      )}

                      <TableCell className="py-4">
                        <div className="flex items-center gap-2 max-w-[200px]" title={item.sharedBy?.fullName || ""}>
                          <User className="w-4 h-4 text-gray-400 shrink-0" />
                          <span className="text-sm font-semibold text-gray-700 truncate">
                            {item.sharedBy?.fullName || "—"}
                          </span>
                        </div>
                      </TableCell>

                      <TableCell className="py-4">
                        <div className="flex flex-col max-w-[250px]">
                          <span className="text-sm font-semibold text-gray-800 truncate" title={recipientLabel}>
                            {recipientLabel}
                          </span>
                          <span className="text-xs text-gray-400 mt-0.5 truncate" title={recipientSub}>
                            {recipientSub}
                          </span>
                        </div>
                      </TableCell>

                      <TableCell className="py-4">
                        <PermissionBadge permission={item.permission} />
                      </TableCell>

                      <TableCell className="py-4 text-gray-500 text-sm font-medium">
                        {item.createdAt ? new Date(item.createdAt).toLocaleString("vi-VN") : "—"}
                      </TableCell>

                      <TableCell className="py-4 text-gray-500 text-sm font-medium">
                        {item.expiresAt ? (
                          <div className="flex items-center gap-1">
                            <Clock className="w-3.5 h-3.5 text-amber-500 shrink-0" />
                            <span>{new Date(item.expiresAt).toLocaleString("vi-VN")}</span>
                          </div>
                        ) : (
                          "Không giới hạn"
                        )}
                      </TableCell>

                      <TableCell className="py-4 text-center">
                        <div className="flex justify-center">
                          {getStatusBadge(item.status)}
                        </div>
                      </TableCell>

                      <TableCell className="py-4 text-center">
                        <div className="flex items-center justify-center">
                          {item.status === "ACTIVE" ? (
                            <Button
                              variant="destructive"
                              size="sm"
                              className="h-8 bg-red-600 hover:bg-red-700 text-white font-medium"
                              onClick={() => handleRevoke(item)}
                            >
                              Thu hồi
                            </Button>
                          ) : (
                            <span className="text-gray-400 text-xs font-semibold">—</span>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </div>

        {/* Pagination */}
        <div className="px-6 py-4 border-t border-gray-100 flex items-center justify-between">
          <p className="text-sm text-gray-500">
            Hiển thị <span className="font-semibold text-gray-700">{(currentPage - 1) * 10 + 1}-{Math.min(currentPage * 10, (data?.totalElements ?? 0))}</span> trên tổng số <span className="font-semibold text-gray-700">{data?.totalElements ?? 0}</span> liên kết
          </p>
          <div className="flex items-center">
            {totalPages > 1 && (
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      setCurrentPage((p) => Math.max(1, p - 1));
                    }}
                    className={`h-9 px-3 border border-gray-200 rounded-md text-sm font-medium mr-2 hover:bg-gray-50 text-gray-600 ${currentPage === 1 ? "pointer-events-none opacity-50" : ""}`}
                  />
                </PaginationItem>

                {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                  let pageNum = i + 1;
                  if (totalPages > 5 && currentPage > 3) {
                    pageNum = currentPage - 2 + i;
                  }
                  if (pageNum > totalPages) return null;

                  return (
                    <PaginationItem key={pageNum}>
                      <PaginationLink
                        href="#"
                        isActive={pageNum === currentPage}
                        onClick={(e) => {
                          e.preventDefault();
                          setCurrentPage(pageNum);
                        }}
                        className={`h-9 w-9 text-sm font-medium rounded-md ${pageNum === currentPage ? "bg-[#EDE9FE] text-[#7C3AED] border border-[#d9cdf0]" : "text-gray-600 hover:bg-gray-50 border border-transparent"}`}
                      >
                        {pageNum}
                      </PaginationLink>
                    </PaginationItem>
                  );
                })}

                <PaginationItem>
                  <PaginationNext
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      setCurrentPage((p) => Math.min(totalPages, p + 1));
                    }}
                    className={`h-9 px-3 border border-gray-200 rounded-md text-sm font-medium ml-2 hover:bg-gray-50 text-gray-600 ${currentPage === totalPages ? "pointer-events-none opacity-50" : ""}`}
                  />
                </PaginationItem>
              </PaginationContent>
            )}
          </div>
        </div>
      </div>

      <AlertDialog open={!!revokeTarget} onOpenChange={(open) => { if (!open) setRevokeTarget(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Thu hồi quyền chia sẻ</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn có chắc chắn muốn thu hồi liên kết chia sẻ của {revokeTarget?.resourceType === "file" ? "tệp tin" : "thư mục"}{" "}
              <span className="font-semibold text-gray-900">
                ({revokeTarget?.fileId || revokeTarget?.folderId})
              </span>{" "}
              này không? Người nhận sẽ không còn khả năng truy cập tài nguyên này.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isRevoking}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleConfirmRevoke}
              disabled={isRevoking}
              className="bg-red-600 text-white hover:bg-red-700"
            >
              {isRevoking && <Loader2 className="mr-2 h-4 w-4 animate-spin inline" />}
              Thu hồi
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

// Wrap with custom layout pagination sub-components wrapper
import {
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

export default function AdminSharesPage() {
  return (
    <Suspense
      fallback={
        <div className="flex h-full items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      }
    >
      <AdminSharesPageInner />
    </Suspense>
  );
}
