"use client";

import { useState } from "react";
import useSWR from "swr";
import { Search, Plus, MoreVertical, Users } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Pagination,
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
import { listTenants, updateTenant, activateTenant } from "@/lib/api/tenants";
import { useDebounce } from "@/hooks/use-debounce";
import {
  CreateTenantDialog,
  EditTenantDialog,
  DeactivateTenantDialog,
} from "@/components/tenants/tenant-dialogs";
import type { TenantResponse } from "@/types/tenant/tenant";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { formatBytes } from "@/lib/utils/storage";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

export default function TenantsPage() {
  const [currentPage, setCurrentPage] = useState(1);
  const [search, setSearch] = useState("");
  const [filterByStatus, setFilterByStatus] = useState<string>("all");
  const [createOpen, setCreateOpen] = useState(false);
  const [editTenant, setEditTenant] = useState<TenantResponse | null>(null);
  const [deactivateTenant, setDeactivateTenant] = useState<TenantResponse | null>(null);
  const [isActivating, setIsActivating] = useState<string | null>(null);
  
  const debouncedSearch = useDebounce(search, 400);

  const { data, isLoading, mutate } = useSWR(
    ["tenants", currentPage, debouncedSearch, filterByStatus],
    ([, page, q, status]) => listTenants({ 
      page, 
      limit: 5, 
      search: q || undefined,
      isActive: status === "active" ? true : status === "inactive" ? false : undefined
    })
  );

  const tenants = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;

  function handleSearchChange(value: string) {
    setSearch(value);
    setCurrentPage(1);
  }

  function handleStatusFilterChange(value: string) {
    setFilterByStatus(value);
    setCurrentPage(1);
  }

  function handleSuccess() {
    mutate();
  }

  async function handleActivateTenant(tenant: TenantResponse) {
    try {
      setIsActivating(tenant.id);
      await activateTenant(tenant.id);
      toast.success(`Đã kích hoạt tổ chức "${tenant.name}"`);
      mutate();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Không thể kích hoạt tổ chức");
    } finally {
      setIsActivating(null);
    }
  }

  function getAvatarFromName(name: string) {
    return `https://api.dicebear.com/7.x/initials/svg?seed=${name}&backgroundColor=2563EB`;
  }

  const getStatusBadge = (isActive: boolean, isSuspended: boolean = false, isPending: boolean = false) => {
    if (isSuspended) {
      return (
        <Badge variant="outline" className="bg-red-50 text-red-600 border-red-100 font-medium px-3 flex items-center justify-center min-w-[80px]">
          Đình chỉ
        </Badge>
      );
    }
    if (isPending) {
      return (
        <Badge variant="outline" className="bg-gray-100 text-gray-600 border-gray-200 font-medium px-3 flex items-center justify-center min-w-[80px]">
          Chờ duyệt
        </Badge>
      );
    }
    return isActive ? (
      <Badge variant="outline" className="bg-white text-gray-700 border-gray-200 font-medium px-3 flex items-center justify-center min-w-[80px]">
        Hoạt động
      </Badge>
    ) : (
      <Badge variant="outline" className="bg-gray-50 text-gray-500 border-gray-200 font-medium px-3 flex items-center justify-center min-w-[80px]">
        Vô hiệu hóa
      </Badge>
    );
  };

  return (
    <div className="bg-gray-50 min-h-[calc(100vh-2rem)] p-4 md:p-8">
      {/* Top Search & Actions */}
      <div className="flex flex-col md:flex-row gap-4 justify-between items-center mb-8">
        <div className="relative w-full md:max-w-xl">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
          <Input
            className="h-12 pl-10 border-0 shadow-sm bg-white focus-visible:ring-[#7C3AED] rounded-lg text-base"
            placeholder="Tìm kiếm tổ chức..."
            value={search}
            onChange={(e) => handleSearchChange(e.target.value)}
          />
        </div>
        <Button 
          className="h-12 bg-[#7C3AED] hover:bg-[#6D28D9] text-white px-6 font-medium gap-2 rounded-lg text-base w-full md:w-auto"
          onClick={() => setCreateOpen(true)}
        >
          <Plus className="h-5 w-5" />
          Thêm tổ chức mới
        </Button>
      </div>

      {/* Main Content Box */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
        {/* Header */}
        <div className="px-6 py-5 border-b border-gray-100 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h2 className="text-xl font-bold text-gray-900 leading-tight">Quản lý Tổ chức</h2>
            <p className="text-sm text-gray-500 mt-1">Quản lý các tài khoản tổ chức, hạn mức dung lượng và quyền truy cập nền tảng.</p>
          </div>
          <div className="flex items-center gap-3">
            <div className="relative">
              <Select value={filterByStatus} onValueChange={handleStatusFilterChange}>
                <SelectTrigger className="w-[180px] h-10 border-gray-200">
                  <SelectValue placeholder="Lọc theo trạng thái" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">Tất cả trạng thái</SelectItem>
                  <SelectItem value="active">Hoạt động</SelectItem>
                  <SelectItem value="inactive">Vô hiệu hóa</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <Button variant="ghost" size="icon" className="h-10 w-10 text-gray-500 rounded-md">
              <MoreVertical className="h-5 w-5" />
            </Button>
          </div>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          <Table className="min-w-[800px]">
            <TableHeader>
              <TableRow className="hover:bg-transparent border-b border-gray-100">
                <TableHead className="font-semibold text-gray-700 py-4 h-auto px-6">Tổ chức</TableHead>
                <TableHead className="font-semibold text-gray-700 py-4 h-auto">Dung lượng</TableHead>
                <TableHead className="font-semibold text-gray-700 py-4 h-auto text-center">Trạng thái</TableHead>
                <TableHead className="font-semibold text-gray-700 py-4 h-auto">Thành viên</TableHead>
                <TableHead className="font-semibold text-gray-700 py-4 h-auto">Cập nhật lúc</TableHead>
                <TableHead className="font-semibold text-gray-700 py-4 h-auto text-center">Thao tác</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={6} className="h-32 text-center text-gray-500">
                    Đang tải...
                  </TableCell>
                </TableRow>
              ) : tenants.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="h-32 text-center text-gray-500">
                    {debouncedSearch ? `Không tìm thấy tổ chức nào khớp với "${debouncedSearch}"` : "Không tìm thấy tổ chức nào."}
                  </TableCell>
                </TableRow>
              ) : (
                tenants.map((tenant) => {
                  const usedGB = (tenant.storageUsedBytes / (1024 * 1024 * 1024)).toFixed(1);
                  const percentage = tenant.storageQuotaBytes ? Math.min(100, Math.round((tenant.storageUsedBytes / tenant.storageQuotaBytes) * 100)) : 0;
                  
                  return (
                    <TableRow key={tenant.id} className="hover:bg-gray-50/50 border-b border-gray-100">
                      <TableCell className="px-6 py-4">
                        <div className="flex items-center gap-4">
                          <Avatar className="h-10 w-10 shadow-sm border border-gray-100">
                            <AvatarImage src={getAvatarFromName(tenant.name)} />
                            <AvatarFallback>{tenant.name.charAt(0)}</AvatarFallback>
                          </Avatar>
                          <div className="flex flex-col max-w-[200px]">
                            <span className="font-bold text-gray-900 text-sm leading-tight truncate" title={tenant.name}>{tenant.name}</span>
                            <span className="text-gray-500 text-xs mt-0.5 truncate" title={tenant.slug}>{tenant.slug}</span>
                          </div>
                        </div>
                      </TableCell>
                      
                      <TableCell className="py-4">
                        <div className="flex flex-col gap-1.5 w-[160px]">
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-semibold text-gray-700">
                              {formatBytes(tenant.storageUsedBytes)}
                            </span>
                            <span className="text-xs text-gray-500">
                              / {tenant.storageQuotaBytes ? formatBytes(tenant.storageQuotaBytes) : 'Không giới hạn'}
                            </span>
                          </div>
                          <div className="h-1.5 w-full bg-gray-100 rounded-full overflow-hidden">
                            <div 
                              className="h-full bg-[#AF8BE0] rounded-full" 
                              style={{ width: `${percentage}%` }}
                            />
                          </div>
                        </div>
                      </TableCell>
                      
                      <TableCell className="py-4 text-center">
                        <div className="flex justify-center">
                          {getStatusBadge(tenant.isActive)}
                        </div>
                      </TableCell>
                      
                      <TableCell className="py-4">
                        <div className="flex items-center gap-1.5 text-gray-500 font-medium">
                          <Users className="h-4 w-4" />
                          <span className="text-sm">{tenant.userCount}</span>
                        </div>
                      </TableCell>

                      <TableCell className="py-4">
                        <span className="text-sm text-gray-500 font-medium">
                          {tenant.updatedAt ? new Date(tenant.updatedAt).toLocaleDateString() : new Date(tenant.createdAt).toLocaleDateString()}
                        </span>
                      </TableCell>
                      
                      <TableCell className="py-4">
                        <div className="flex items-center justify-center gap-3">
                          {tenant.isActive ? (
                            <Button
                              variant="default"
                              className="bg-[#E11D48] hover:bg-[#BE123C] text-white h-9 px-6 rounded-md font-medium shadow-none"
                              onClick={() => setDeactivateTenant(tenant)}
                            >
                              Vô hiệu hóa
                            </Button>
                          ) : (
                            <Button
                              variant="default"
                              className="bg-emerald-600 hover:bg-emerald-700 text-white h-9 px-6 rounded-md font-medium shadow-none"
                              onClick={() => handleActivateTenant(tenant)}
                              disabled={isActivating === tenant.id}
                            >
                              {isActivating === tenant.id ? "Đang kích hoạt..." : "Kích hoạt"}
                            </Button>
                          )}
                          <Button
                            variant="default"
                            className="bg-[#7C3AED] hover:bg-[#6D28D9] text-white h-9 px-6 rounded-md font-medium shadow-none"
                            onClick={() => setEditTenant(tenant)}
                          >
                            Chi tiết
                          </Button>
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
            Hiển thị <span className="font-semibold text-gray-700">{(currentPage - 1) * 5 + 1}-{Math.min(currentPage * 5, (data?.totalElements ?? 0))}</span> trên tổng số <span className="font-semibold text-gray-700">{data?.totalElements ?? 0}</span> tổ chức
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
                  // standard simple logic for demo
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

      <CreateTenantDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSuccess={handleSuccess}
      />

      <EditTenantDialog
        tenant={editTenant}
        open={editTenant !== null}
        onOpenChange={(open) => { if (!open) setEditTenant(null); }}
        onSuccess={handleSuccess}
      />

      <DeactivateTenantDialog
        tenant={deactivateTenant}
        open={deactivateTenant !== null}
        onOpenChange={(open) => { if (!open) setDeactivateTenant(null); }}
        onSuccess={handleSuccess}
      />
    </div>
  );
}
