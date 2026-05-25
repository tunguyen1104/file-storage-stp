import React, { memo } from "react";
import { ArrowUp, ArrowDown, FolderOpen, Pencil, MoveRight, Info, Trash2, RotateCcw, Download, Link, Share2, MoreVertical } from "lucide-react";
import { cn } from "@/lib/utils";
import { renderFileIcon, getTypeName, getStatusBadge, formatBytes } from "./page";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";

export const AdminFileTable = memo(function AdminFileTable({
  items,
  isLoading,
  sortBy,
  sortDirection,
  selectedFileId,
  user,
  handleSort,
  handleItemSelect,
  handleContextMenu,
  handleAction
}: any) {
  const SortIcon = ({ column }: { column: string }) => {
    if (sortBy !== column) return null;
    return sortDirection === "ASC" ? <ArrowUp className="w-3 h-3 ml-1" /> : <ArrowDown className="w-3 h-3 ml-1" />;
  };

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
      <table className="w-full text-sm">
        <thead className="border-b border-gray-100 bg-gray-50">
          <tr>
            <th className={cn("px-4 py-3 text-left text-xs font-semibold cursor-pointer hover:bg-gray-100 select-none transition-colors", sortBy === "NAME" ? "text-primary" : "text-gray-500")} onClick={() => handleSort("NAME")}>
              <div className="flex items-center">Tên <SortIcon column="NAME" /></div>
            </th>
            <th className={cn("px-4 py-3 text-left text-xs font-semibold cursor-pointer hover:bg-gray-100 select-none transition-colors", sortBy === "TENANT_NAME" ? "text-primary" : "text-gray-500")} onClick={() => handleSort("TENANT_NAME")}>
              <div className="flex items-center">Tổ chức <SortIcon column="TENANT_NAME" /></div>
            </th>
            <th className={cn("px-4 py-3 text-left text-xs font-semibold hidden sm:table-cell cursor-pointer hover:bg-gray-100 select-none transition-colors", sortBy === "CREATED_AT" ? "text-primary" : "text-gray-500")} onClick={() => handleSort("CREATED_AT")}>
              <div className="flex items-center">Ngày sửa đổi <SortIcon column="CREATED_AT" /></div>
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 hidden md:table-cell">Loại</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500">Trạng thái</th>
            <th className={cn("px-4 py-3 text-right text-xs font-semibold cursor-pointer hover:bg-gray-100 select-none transition-colors", sortBy === "SIZE_BYTES" ? "text-primary" : "text-gray-500")} onClick={() => handleSort("SIZE_BYTES")}>
              <div className="flex items-center justify-end">Dung lượng <SortIcon column="SIZE_BYTES" /></div>
            </th>
            <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            Array.from({ length: 6 }).map((_, i) => (
              <tr key={i} className="border-b border-gray-50">
                <td className="px-4 py-3"><div className="h-4 w-40 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-3"><div className="h-4 w-24 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-3"><div className="h-4 w-28 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-3"><div className="h-4 w-16 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-3"><div className="h-4 w-16 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-3 text-right"><div className="ml-auto h-4 w-14 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-3 text-center"><div className="mx-auto h-4 w-8 animate-pulse rounded bg-gray-100" /></td>
              </tr>
            ))
          ) : items.length === 0 ? (
            <tr>
              <td colSpan={7} className="px-4 py-16 text-center text-gray-400">
                <FolderOpen className="h-10 w-10 text-gray-300 mx-auto mb-3" />
                Không tìm thấy tệp hoặc thư mục nào.
              </td>
            </tr>
          ) : (
            items.map((item: any) => {
              const isSelected = selectedFileId === item.id;
              const name = item.name || (item as any).originalName;
              const date = item.updatedAt || item.createdAt;
              return (
                <tr
                  key={item.id}
                  onClick={() => handleItemSelect(item)}
                  onContextMenu={(e) => handleContextMenu(e, item)}
                  className={cn(
                    "group border-b border-gray-50 cursor-pointer transition-colors",
                    isSelected
                      ? "bg-primary/10"
                      : "hover:bg-gray-50"
                  )}
                >
                  <td className="px-4 py-3 min-w-0 max-w-[200px] sm:max-w-[300px]">
                    <div className="flex items-center gap-2.5 min-w-0">
                      {renderFileIcon(item.mimeType, "h-4 w-4 shrink-0")}
                      <span className={cn(
                        "truncate font-medium block",
                        isSelected ? "text-primary font-semibold" : "text-gray-800"
                      )} title={name}>
                        {name}
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-gray-500 text-xs min-w-0 max-w-[120px] sm:max-w-[180px]">
                    <span className="truncate block" title={item.tenantName || undefined}>
                      {item.tenantName || <span className="text-gray-300">—</span>}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-400 hidden sm:table-cell whitespace-nowrap">
                    {date && new Date(date).toLocaleString("vi-VN", {
                      month: "short",
                      day: "numeric",
                      year: "numeric",
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </td>
                  <td className="px-4 py-3 text-gray-500 hidden md:table-cell">
                    {getTypeName(item.mimeType)}
                  </td>
                  <td className="px-4 py-3">
                    {item.type === "folder" || !item.status ? (
                      <span className="text-gray-300">—</span>
                    ) : (
                      getStatusBadge(item.status)
                    )}
                  </td>
                  <td className="px-4 py-3 text-right text-gray-400 tabular-nums whitespace-nowrap">
                    {item.type === "folder" || !item.sizeBytes ? "—" : formatBytes(item.sizeBytes)}
                  </td>
                  <td className="px-4 py-3 text-center" onClick={(e) => e.stopPropagation()}>
                    {item.type === "file" && item.status === "DELETED" ? (
                      <span className="text-gray-300">—</span>
                    ) : (
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-8 w-8 text-gray-400 hover:text-gray-600 focus:outline-none"
                          >
                            <MoreVertical className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-52">
                          {(() => {
                            const isOwnedByMe = item.actor?.id === user?.id;
                            const isSystem =
                              !item.actor ||
                              !item.actor.id ||
                              item.actor.fullName === "System" ||
                              (item as any).uploadedBy?.fullName === "System";
                            const isOwnedByMeOrSystem = isOwnedByMe || isSystem;
                            const isFolder = item.type === "folder" || !item.mimeType;

                            if (isFolder) {
                              if (isOwnedByMeOrSystem) {
                                return (
                                  <>
                                    <DropdownMenuItem
                                      onClick={(e) => handleAction(e, "rename", item)}
                                      className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                                    >
                                      <Pencil className="h-4 w-4 text-gray-500" />
                                      Đổi tên
                                    </DropdownMenuItem>
                                    <DropdownMenuItem
                                      onClick={(e) => handleAction(e, "move", item)}
                                      className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                                    >
                                      <MoveRight className="h-4 w-4 text-gray-500" />
                                      Di chuyển
                                    </DropdownMenuItem>
                                    <DropdownMenuSeparator className="my-1" />
                                    <DropdownMenuItem
                                      onClick={(e) => handleAction(e, "delete", item)}
                                      className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-red-600 focus:text-red-700 focus:bg-red-50 font-medium"
                                    >
                                      <Trash2 className="h-4 w-4 text-red-500" />
                                      Xóa vĩnh viễn
                                    </DropdownMenuItem>
                                  </>
                                );
                              } else {
                                return (
                                  <DropdownMenuItem
                                    onClick={(e) => handleAction(e, "delete", item)}
                                    className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-red-600 focus:text-red-700 focus:bg-red-50 font-medium"
                                  >
                                    <Trash2 className="h-4 w-4 text-red-500" />
                                    Xóa vĩnh viễn
                                  </DropdownMenuItem>
                                );
                              }
                            } else {
                              // It is a File
                              const isPendingDelete = item.status === "PENDING_DELETE";
                              const isPendingScanOrError =
                                item.status === "PENDING" ||
                                item.status === "SCANNING" ||
                                item.status === "UPLOAD_FAILED" ||
                                item.status === "INFECTED";

                              if (isPendingScanOrError) {
                                return (
                                  <DropdownMenuItem
                                    onClick={(e) => handleAction(e, "delete-permanent", item)}
                                    className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-red-600 focus:text-red-700 focus:bg-red-50 font-medium"
                                  >
                                    <Trash2 className="h-4 w-4 text-red-500" />
                                    Xóa vĩnh viễn
                                  </DropdownMenuItem>
                                );
                              }

                              if (isPendingDelete) {
                                return (
                                  <>
                                    <DropdownMenuItem
                                      onClick={(e) => handleAction(e, "restore", item)}
                                      className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                                    >
                                      <RotateCcw className="h-4 w-4 text-gray-500" />
                                      Khôi phục
                                    </DropdownMenuItem>
                                    <DropdownMenuSeparator className="my-1" />
                                    <DropdownMenuItem
                                      onClick={(e) => handleAction(e, "delete-permanent", item)}
                                      className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-red-600 focus:text-red-700 focus:bg-red-50 font-medium"
                                    >
                                      <Trash2 className="h-4 w-4 text-red-500" />
                                      Xóa vĩnh viễn
                                    </DropdownMenuItem>
                                  </>
                                );
                              } else {
                                // Active/Scanning/Infected states
                                if (isOwnedByMeOrSystem) {
                                  return (
                                    <>
                                      <DropdownMenuItem
                                        onClick={(e) => handleAction(e, "download", item)}
                                        className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                                      >
                                        <Download className="h-4 w-4 text-gray-500" />
                                        Tải xuống
                                      </DropdownMenuItem>
                                      <DropdownMenuItem
                                        onClick={(e) => handleAction(e, "rename", item)}
                                        className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                                      >
                                        <Pencil className="h-4 w-4 text-gray-500" />
                                        Đổi tên
                                      </DropdownMenuItem>
                                      <DropdownMenuSeparator className="my-1" />
                                      {!isFolder && item.status === "ACTIVE" && (
                                        <DropdownMenuItem
                                          onClick={(e) => handleAction(e, "copy-link", item)}
                                          className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                                        >
                                          <Link className="h-4 w-4 text-gray-500" />
                                          Sao chép liên kết
                                        </DropdownMenuItem>
                                      )}
                                      <DropdownMenuItem
                                        onClick={(e) => handleAction(e, "share", item)}
                                        className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                                      >
                                        <Share2 className="h-4 w-4 text-gray-500" />
                                        Chia sẻ
                                      </DropdownMenuItem>
                                      <DropdownMenuItem
                                        onClick={(e) => handleAction(e, "move", item)}
                                        className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                                      >
                                        <MoveRight className="h-4 w-4 text-gray-500" />
                                        Di chuyển
                                      </DropdownMenuItem>
                                      <DropdownMenuItem
                                        onClick={(e) => handleAction(e, "info", item)}
                                        className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                                      >
                                        <Info className="h-4 w-4 text-gray-500" />
                                        Thông tin về tệp
                                      </DropdownMenuItem>
                                      <DropdownMenuSeparator className="my-1" />
                                      <DropdownMenuItem
                                        onClick={(e) => handleAction(e, "delete", item)}
                                        className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                                      >
                                        <Trash2 className="h-4 w-4 text-gray-500" />
                                        Chuyển vào thùng rác
                                      </DropdownMenuItem>
                                    </>
                                  );
                                } else {
                                  return (
                                    <DropdownMenuItem
                                      onClick={(e) => handleAction(e, "delete-permanent", item)}
                                      className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-red-600 focus:text-red-700 focus:bg-red-50 font-medium"
                                    >
                                      <Trash2 className="h-4 w-4 text-red-500" />
                                      Xóa vĩnh viễn
                                    </DropdownMenuItem>
                                  );
                                }
                              }
                            }
                          })()}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    )}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
});
