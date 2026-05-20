"use client";

import { useState, useEffect } from "react";
import useSWR from "swr";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import {
  Mail,
  Clock,
  ShieldCheck,
  Eye,
  Trash2,
  Plus,
  Users,
  User,
  Building,
  Calendar,
  AlertCircle,
} from "lucide-react";
import { useAuth } from "@/hooks/use-auth";
import { listTenants } from "@/lib/api/tenants";
import {
  shareFileWithUser,
  shareFolderWithUser,
  shareFileWithTenant,
  shareFolderWithTenant,
  listSharesByMe,
  revokeShare,
  extendShare,
} from "@/lib/api/share";
import { ExtendShareDialog } from "./ExtendShareDialog";

interface ShareItemDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  item: {
    id: string;
    name: string;
    type: "FOLDER" | "FILE";
    mimeType?: string | null;
  } | null;
}

export function ShareItemDialog({ open, onOpenChange, item }: ShareItemDialogProps) {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === "SUPER_ADMIN";
  const isTenantAdmin = user?.role === "TENANT_ADMIN";

  // Share with User state
  const [userEmail, setUserEmail] = useState("");
  const [userExpireTime, setUserExpireTime] = useState("60"); // in minutes
  const [userPermission, setUserPermission] = useState<"VIEW" | "EDIT">("VIEW");

  // Share with Tenant state
  const [tenantId, setTenantId] = useState<string>("ALL");
  const [tenantExpireTime, setTenantExpireTime] = useState("60");

  const [activeTab, setActiveTab] = useState<"user" | "tenant">("user");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [extendShareId, setExtendShareId] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setActiveTab(isSuperAdmin ? "tenant" : "user");
    }
  }, [open, isSuperAdmin]);

  // SWR to fetch active shares by me for this item
  const { data: sharesData, mutate: refreshShares } = useSWR(
    open && item ? ["shares-by-me", item.id] : null,
    () => listSharesByMe({ page: 1, limit: 100 }),
    { revalidateOnFocus: false }
  );

  // SWR to fetch tenants if SUPER_ADMIN
  const { data: tenantsData } = useSWR(
    open && isSuperAdmin ? "shares-tenants-list" : null,
    () => listTenants({ page: 1, limit: 30 }),
    { revalidateOnFocus: false }
  );

  const tenants = tenantsData?.data ?? [];

  // Filter shares specifically for this item
  const activeShares = (sharesData?.data ?? []).filter((s) => {
    if (!item) return false;
    return item.type === "FOLDER" ? s.folderId === item.id : s.fileId === item.id;
  });

  const handleShareWithUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!item) return;

    if (!userEmail) {
      toast.error("Vui lòng nhập email người nhận");
      return;
    }

    const expireMin = parseInt(userExpireTime, 10);
    if (isNaN(expireMin) || expireMin <= 0) {
      toast.error("Thời gian hết hạn phải là số phút lớn hơn 0");
      return;
    }

    setIsSubmitting(true);
    try {
      if (item.type === "FOLDER") {
        await shareFolderWithUser(item.id, userEmail, userPermission, expireMin);
      } else {
        await shareFileWithUser(item.id, userEmail, expireMin);
      }
      toast.success(`Đã chia sẻ thành công với ${userEmail}`);
      setUserEmail("");
      refreshShares();
    } catch (err: any) {
      toast.error(err instanceof Error ? err.message : "Chia sẻ thất bại");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleShareWithTenant = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!item) return;

    const expireMin = parseInt(tenantExpireTime, 10);
    if (isNaN(expireMin) || expireMin <= 0) {
      toast.error("Thời gian hết hạn phải là số phút lớn hơn 0");
      return;
    }

    setIsSubmitting(true);
    try {
      // For TENANT_ADMIN, they can only share with their own tenant.
      // So they don't select a specific ID, they can just pass null/undefined or their own tenant id.
      // Let's pass the selected tenantId if SUPER_ADMIN, otherwise undefined/null
      const tId = isSuperAdmin ? (tenantId === "ALL" ? null : tenantId) : null;

      if (item.type === "FOLDER") {
        await shareFolderWithTenant(item.id, expireMin, tId);
      } else {
        await shareFileWithTenant(item.id, expireMin, tId);
      }

      toast.success(
        isSuperAdmin && tenantId === "ALL"
          ? "Đã chia sẻ thành công với tất cả Tenant"
          : "Đã chia sẻ thành công với Tenant"
      );
      refreshShares();
    } catch (err: any) {
      toast.error(err instanceof Error ? err.message : "Chia sẻ thất bại");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRevoke = async (shareId: string) => {
    try {
      await revokeShare(shareId);
      toast.success("Đã thu hồi quyền chia sẻ thành công");
      refreshShares();
    } catch (err: any) {
      toast.error(err instanceof Error ? err.message : "Thu hồi thất bại");
    }
  };

  const handleExtend = (shareId: string) => {
    setExtendShareId(shareId);
  };

  const getAvatarUrl = (name: string) => {
    return `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(name)}`;
  };

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[540px] rounded-xl border border-gray-100 bg-white p-6 shadow-2xl focus-visible:outline-none">
        <DialogHeader className="mb-4">
          <DialogTitle className="flex items-center gap-2 text-lg font-bold text-gray-900">
            <Users className="h-5 w-5 text-primary" />
            Chia sẻ &quot;{item?.name}&quot;
          </DialogTitle>
        </DialogHeader>

        {/* Dynamic Share Tabs */}
        <Tabs value={activeTab} onValueChange={(v: any) => setActiveTab(v)} className="w-full">
          {isTenantAdmin && (
            <TabsList className="grid w-full grid-cols-2 rounded-lg bg-gray-50 p-1 mb-5">
              <TabsTrigger
                value="user"
                className="rounded-md py-1.5 text-xs font-semibold text-gray-500 transition-all data-[state=active]:bg-white data-[state=active]:text-primary data-[state=active]:shadow-sm"
              >
                <User className="mr-1.5 h-3.5 w-3.5" />
                Chia sẻ với User
              </TabsTrigger>
              <TabsTrigger
                value="tenant"
                className="rounded-md py-1.5 text-xs font-semibold text-gray-500 transition-all data-[state=active]:bg-white data-[state=active]:text-primary data-[state=active]:shadow-sm"
              >
                <Building className="mr-1.5 h-3.5 w-3.5" />
                Chia sẻ với Tenant
              </TabsTrigger>
            </TabsList>
          )}

          {/* 1. Share with User Form */}
          {!isSuperAdmin && (
            <TabsContent value="user" className="mt-0">
              <form onSubmit={handleShareWithUser} className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="userEmail" className="text-xs font-bold text-gray-700">
                    Email người nhận
                  </Label>
                  <div className="relative">
                    <Mail className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                    <Input
                      id="userEmail"
                      type="email"
                      placeholder="name@company.com"
                      value={userEmail}
                      onChange={(e) => setUserEmail(e.target.value)}
                      className="h-10 border-gray-200 bg-gray-50 pl-10 text-sm focus-visible:ring-primary/20"
                      required
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="userExpireTime" className="text-xs font-bold text-gray-700">
                      Thời gian chia sẻ
                    </Label>
                    <Select value={userExpireTime} onValueChange={setUserExpireTime}>
                      <SelectTrigger className="h-10 border-gray-200 bg-gray-50 text-sm">
                        <SelectValue placeholder="Chọn thời gian" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="30">30 phút</SelectItem>
                        <SelectItem value="60">1 giờ</SelectItem>
                        <SelectItem value="1440">1 ngày</SelectItem>
                        <SelectItem value="10080">1 tuần</SelectItem>
                        <SelectItem value="43200">1 tháng</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="userPermission" className="text-xs font-bold text-gray-700">
                      Quyền hạn
                    </Label>
                    <Select
                      value={userPermission}
                      onValueChange={(v: any) => setUserPermission(v)}
                      disabled={item?.type === "FILE"}
                    >
                      <SelectTrigger className="h-10 border-gray-200 bg-gray-50 text-sm">
                        <SelectValue placeholder="Chọn quyền" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="VIEW">Xem tệp</SelectItem>
                        {item?.type === "FOLDER" && <SelectItem value="EDIT">Chỉnh sửa</SelectItem>}
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                <div className="p-3 bg-blue-50 border border-blue-100 rounded-lg text-blue-800 text-xs font-medium flex items-start gap-2">
                  <AlertCircle className="h-4 w-4 shrink-0 text-blue-600 mt-0.5" />
                  <span>Ghi chú: Người dùng nhận chia sẻ phải thuộc cùng một tổ chức (tenant) với bạn.</span>
                </div>

                <Button
                  type="submit"
                  disabled={isSubmitting}
                  className="w-full bg-primary hover:bg-primary/95 text-white gap-2 font-medium h-10 shadow-md shadow-primary/10 transition-all rounded-lg"
                >
                  <Plus className="h-4 w-4" />
                  Cấp quyền truy cập
                </Button>
              </form>
            </TabsContent>
          )}

          {/* 2. Share with Tenant Form */}
          {(isSuperAdmin || isTenantAdmin) && (
            <TabsContent value="tenant" className="mt-0">
              <form onSubmit={handleShareWithTenant} className="space-y-4">
                {isSuperAdmin && (
                  <div className="space-y-1.5">
                    <Label htmlFor="tenantId" className="text-xs font-bold text-gray-700">
                      Chọn Tenant được chia sẻ
                    </Label>
                    <Select value={tenantId} onValueChange={setTenantId}>
                      <SelectTrigger className="h-10 border-gray-200 bg-gray-50 text-sm">
                        <SelectValue placeholder="Chọn Tenant" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="ALL">Tất cả Tenant</SelectItem>
                        {tenants.map((t) => (
                          <SelectItem key={t.id} value={t.id}>
                            {t.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {!isSuperAdmin && isTenantAdmin && (
                  <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg text-blue-800 text-xs font-medium">
                    Là Tenant Admin, bạn sẽ chia sẻ tài nguyên này cho toàn bộ thành viên trong tổ chức của bạn.
                  </div>
                )}

                <div className="space-y-1.5">
                  <Label htmlFor="tenantExpireTime" className="text-xs font-bold text-gray-700">
                    Thời gian chia sẻ
                  </Label>
                  <Select value={tenantExpireTime} onValueChange={setTenantExpireTime}>
                    <SelectTrigger className="h-10 border-gray-200 bg-gray-50 text-sm">
                      <SelectValue placeholder="Chọn thời gian" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="30">30 phút</SelectItem>
                      <SelectItem value="60">1 giờ</SelectItem>
                      <SelectItem value="1440">1 ngày</SelectItem>
                      <SelectItem value="10080">1 tuần</SelectItem>
                      <SelectItem value="43200">1 tháng</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <Button
                  type="submit"
                  disabled={isSubmitting}
                  className="w-full bg-primary hover:bg-primary/95 text-white gap-2 font-medium h-10 shadow-md shadow-primary/10 transition-all rounded-lg"
                >
                  <Plus className="h-4 w-4" />
                  Cấp quyền cho Tenant
                </Button>
              </form>
            </TabsContent>
          )}
        </Tabs>

        {/* 3. Who has access - Google Drive Style list */}
        <div className="mt-6 pt-5 border-t border-gray-100">
          <p className="text-xs font-bold text-gray-900 mb-3.5 flex items-center gap-1.5">
            <ShieldCheck className="h-4 w-4 text-emerald-600" />
            Những ai có quyền truy cập
          </p>

          <div className="max-h-[220px] overflow-y-auto space-y-3 pr-1 hide-scrollbar">
            {/* Owner Row */}
            <div className="flex items-center justify-between py-1">
              <div className="flex items-center gap-2.5 min-w-0">
                <Avatar className="h-8 w-8 shrink-0">
                  <AvatarImage src={getAvatarUrl(user?.fullName || "Owner")} />
                  <AvatarFallback className="bg-gray-100 text-gray-500 font-semibold text-xs">
                    {(user?.fullName || "O")[0]}
                  </AvatarFallback>
                </Avatar>
                <div className="min-w-0">
                  <p className="text-xs font-semibold text-gray-800 truncate">
                    {user?.fullName} (Bạn)
                  </p>
                  <p className="text-[10px] text-gray-400 truncate">{user?.email}</p>
                </div>
              </div>
              <Badge variant="outline" className="bg-gray-50 text-gray-600 border-gray-200 text-[10px] font-bold py-0.5 px-2 rounded-full shrink-0">
                Chủ sở hữu
              </Badge>
            </div>

            {/* Active Shares list */}
            {activeShares.length === 0 ? (
              <p className="text-[11px] text-gray-400 text-center py-6">
                Chưa có ai khác được chia sẻ tài nguyên này.
              </p>
            ) : (
              activeShares.map((share) => {
                const isUserType = share.recipientType === "USER";
                const labelName = isUserType
                  ? share.sharedWithUser?.fullName || share.sharedWithUser?.email
                  : share.sharedWithTenant?.name || "Tất cả Tenant";
                const subLabel = isUserType
                  ? share.sharedWithUser?.email
                  : share.sharedWithTenant?.name ? "Tenant Access" : "Global Public Access";

                const isExpired = share.status === "EXPIRED";
                const isRevoked = share.status === "REVOKED";

                return (
                  <div
                    key={share.id}
                    className="flex items-center justify-between py-1.5 border-b border-gray-50 group last:border-0"
                  >
                    <div className="flex items-center gap-2.5 min-w-0 flex-1">
                      <Avatar className="h-8 w-8 shrink-0">
                        <AvatarImage src={getAvatarUrl(labelName || "User")} />
                        <AvatarFallback className="bg-primary/10 text-primary font-semibold text-xs">
                          {isUserType ? <User className="h-4 w-4" /> : <Building className="h-4 w-4" />}
                        </AvatarFallback>
                      </Avatar>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <p className="text-xs font-semibold text-gray-800 truncate max-w-[160px]">
                            {labelName}
                          </p>
                          <Badge
                            variant="outline"
                            className={cn(
                              "text-[9px] py-0 px-1 rounded-full whitespace-nowrap",
                              share.status === "ACTIVE"
                                ? "bg-emerald-50 text-emerald-700 border-emerald-100"
                                : isExpired
                                  ? "bg-amber-50 text-amber-700 border-amber-100"
                                  : "bg-rose-50 text-rose-700 border-rose-100"
                            )}
                          >
                            {share.status}
                          </Badge>
                        </div>
                        <p className="text-[10px] text-gray-400 truncate flex items-center gap-2">
                          <span>{subLabel}</span>
                          {share.expiresAt && !isRevoked && (
                            <span className="flex items-center gap-0.5 text-gray-400 whitespace-nowrap font-medium">
                              <Calendar className="h-2.5 w-2.5" />
                              Hết hạn: {new Date(share.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} {new Date(share.expiresAt).toLocaleDateString()}
                            </span>
                          )}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      {/* Permission Display */}
                      <span className="text-[10px] font-bold text-gray-500 mr-2 flex items-center gap-0.5">
                        {share.permission === "EDIT" ? <ShieldCheck className="h-3 w-3 text-emerald-600" /> : <Eye className="h-3 w-3 text-blue-500" />}
                        {share.permission}
                      </span>

                      {share.status !== "REVOKED" && (
                        <>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handleExtend(share.id)}
                            title="Gia hạn chia sẻ"
                            className="h-7 w-7 rounded-full text-gray-400 hover:bg-gray-100 hover:text-gray-700 shrink-0"
                          >
                            <Clock className="h-3.5 w-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handleRevoke(share.id)}
                            title="Thu hồi chia sẻ"
                            className="h-7 w-7 rounded-full text-red-400 hover:bg-red-50 hover:text-red-600 shrink-0"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
        </DialogContent>
      </Dialog>
      <ExtendShareDialog
        open={!!extendShareId}
        onOpenChange={(open) => !open && setExtendShareId(null)}
        shareId={extendShareId || ""}
        resourceName={item?.name}
        onSuccess={refreshShares}
      />
    </>
  );
}
