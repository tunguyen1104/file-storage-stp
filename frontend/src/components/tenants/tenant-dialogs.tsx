"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { HardDrive, Loader2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { createTenant, updateTenant, deactivateTenant } from "@/lib/api/tenants";
import {
  createTenantSchema,
  updateTenantSchema,
  type CreateTenantInput,
  type UpdateTenantInput,
} from "@/lib/schemas/tenant.schema";
import type { TenantResponse } from "@/types/tenant/tenant";
import { parseBytes, convertToBytes, formatBytes } from "@/lib/utils/storage";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

// ─── Create Tenant Dialog ────────────────────────────────────────

interface CreateTenantDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function CreateTenantDialog({
  open,
  onOpenChange,
  onSuccess,
}: CreateTenantDialogProps) {
  const form = useForm<CreateTenantInput>({
    resolver: zodResolver(createTenantSchema),
    defaultValues: { name: "", slug: "", storageQuotaValue: "", storageQuotaUnit: "GB" },
  });

  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleClose(open: boolean) {
    if (!open) form.reset();
    onOpenChange(open);
  }

  function handleNameChange(value: string) {
    form.setValue("name", value);
    const slug = value
      .toLowerCase()
      .replace(/\s+/g, "-")
      .replace(/[^a-z0-9-]/g, "")
      .replace(/-+/g, "-")
      .slice(0, 100);
    form.setValue("slug", slug, { shouldValidate: true });
  }

  async function onSubmit(values: CreateTenantInput) {
    setIsSubmitting(true);
    try {
      await createTenant({
        name: values.name,
        slug: values.slug,
        storageQuotaBytes: values.storageQuotaValue
          ? convertToBytes(Number(values.storageQuotaValue), values.storageQuotaUnit || "GB").toString()
          : "",
      });
      toast.success("Tạo tổ chức thành công");
      form.reset();
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Không thể tạo tổ chức");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Tạo tổ chức mới</DialogTitle>
          <DialogDescription>
            Thêm tổ chức mới. Đường dẫn định danh (slug) phải là duy nhất và an toàn cho URL.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Tên tổ chức *</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Acme Corp"
                      {...field}
                      onChange={(e) => handleNameChange(e.target.value)}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="slug"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Đường dẫn định danh (Slug) *</FormLabel>
                  <FormControl>
                    <Input placeholder="acme-corp" {...field} />
                  </FormControl>
                  <FormDescription>
                    Định danh duy nhất — chỉ chứa chữ thường, số và dấu gạch ngang
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="storageQuotaValue"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    <span className="flex items-center gap-1.5">
                      <HardDrive className="h-3.5 w-3.5" />
                      Hạn mức dung lượng
                    </span>
                  </FormLabel>
                  <div className="flex gap-2">
                    <FormControl>
                      <Input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="Ví dụ: 5"
                        {...field}
                        value={field.value ?? ""}
                        className="flex-1"
                      />
                    </FormControl>
                    <FormField
                      control={form.control}
                      name="storageQuotaUnit"
                      render={({ field: unitField }) => (
                        <Select onValueChange={unitField.onChange} value={unitField.value}>
                          <FormControl>
                            <SelectTrigger className="w-[100px]">
                              <SelectValue placeholder="Đơn vị" />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            <SelectItem value="KB">KB</SelectItem>
                            <SelectItem value="MB">MB</SelectItem>
                            <SelectItem value="GB">GB</SelectItem>
                            <SelectItem value="TB">TB</SelectItem>
                          </SelectContent>
                        </Select>
                      )}
                    />
                  </div>
                  <FormDescription>Để trống để sử dụng hạn mức mặc định của hệ thống</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => handleClose(false)}>
                Hủy
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Tạo tổ chức
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

// ─── Edit Tenant Dialog ──────────────────────────────────────────

interface EditTenantDialogProps {
  tenant: TenantResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function EditTenantDialog({
  tenant,
  open,
  onOpenChange,
  onSuccess,
}: EditTenantDialogProps) {
  const initialQuota = tenant?.storageQuotaBytes ? parseBytes(tenant.storageQuotaBytes) : null;

  const form = useForm<UpdateTenantInput>({
    resolver: zodResolver(updateTenantSchema),
    values: tenant
      ? {
          name: tenant.name,
          slug: tenant.slug,
          storageQuotaValue: initialQuota ? initialQuota.value.toString() : "",
          storageQuotaUnit: initialQuota ? initialQuota.unit : "GB",
        }
      : undefined,
  });

  const [isSubmitting, setIsSubmitting] = useState(false);

  async function onSubmit(values: UpdateTenantInput) {
    if (!tenant) return;
    setIsSubmitting(true);
    try {
      const newQuotaBytes = values.storageQuotaValue
        ? convertToBytes(Number(values.storageQuotaValue), values.storageQuotaUnit || "GB")
        : undefined;

      await updateTenant(tenant.id, {
        name: values.name ?? "",
        slug: values.slug ?? "",
        storageQuotaBytes: newQuotaBytes ? newQuotaBytes.toString() : "",
      });
      toast.success("Cập nhật tổ chức thành công");
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Không thể cập nhật tổ chức");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Chi tiết tổ chức</DialogTitle>
          <DialogDescription>
            Xem và cập nhật thông tin tổ chức. Đường dẫn định danh (slug) phải là duy nhất.
          </DialogDescription>
        </DialogHeader>

        {tenant && (
          <div className="grid grid-cols-2 gap-4 bg-gray-50 p-4 rounded-lg text-sm mb-2">
            <div>
              <span className="text-gray-500 block text-xs mb-1">Trạng thái</span>
              <span className="font-medium">{tenant.isActive ? "Hoạt động" : "Vô hiệu hóa"}</span>
            </div>
            <div>
              <span className="text-gray-500 block text-xs mb-1">Thành viên</span>
              <span className="font-medium">{tenant.userCount}</span>
            </div>
            <div>
              <span className="text-gray-500 block text-xs mb-1">Dung lượng đã dùng</span>
              <span className="font-medium">{formatBytes(tenant.storageUsedBytes)}</span>
            </div>
            <div>
              <span className="text-gray-500 block text-xs mb-1">Ngày tạo</span>
              <span className="font-medium">{new Date(tenant.createdAt).toLocaleString()}</span>
            </div>
            <div>
              <span className="text-gray-500 block text-xs mb-1">Cập nhật lúc</span>
              <span className="font-medium">{tenant.updatedAt ? new Date(tenant.updatedAt).toLocaleString() : "Chưa có"}</span>
            </div>
          </div>
        )}

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Tên tổ chức</FormLabel>
                  <FormControl>
                    <Input placeholder="Acme Corp" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="slug"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Đường dẫn định danh (Slug)</FormLabel>
                  <FormControl>
                    <Input placeholder="acme-corp" {...field} />
                  </FormControl>
                  <FormDescription>
                    Chỉ chứa chữ thường, số và dấu gạch ngang
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="storageQuotaValue"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    <span className="flex items-center gap-1.5">
                      <HardDrive className="h-3.5 w-3.5" />
                      Hạn mức dung lượng
                    </span>
                  </FormLabel>
                  <div className="flex gap-2">
                    <FormControl>
                      <Input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="Ví dụ: 5"
                        {...field}
                        value={field.value ?? ""}
                        className="flex-1"
                      />
                    </FormControl>
                    <FormField
                      control={form.control}
                      name="storageQuotaUnit"
                      render={({ field: unitField }) => (
                        <Select onValueChange={unitField.onChange} value={unitField.value}>
                          <FormControl>
                            <SelectTrigger className="w-[100px]">
                              <SelectValue placeholder="Đơn vị" />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            <SelectItem value="KB">KB</SelectItem>
                            <SelectItem value="MB">MB</SelectItem>
                            <SelectItem value="GB">GB</SelectItem>
                            <SelectItem value="TB">TB</SelectItem>
                          </SelectContent>
                        </Select>
                      )}
                    />
                  </div>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Hủy
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Lưu thay đổi
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

// ─── Deactivate Tenant Dialog ────────────────────────────────────

interface DeactivateTenantDialogProps {
  tenant: TenantResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function DeactivateTenantDialog({
  tenant,
  open,
  onOpenChange,
  onSuccess,
}: DeactivateTenantDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleDeactivate() {
    if (!tenant) return;
    setIsSubmitting(true);
    try {
      await deactivateTenant(tenant.id);
      toast.success(`Đã vô hiệu hóa tổ chức "${tenant.name}"`);
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Không thể vô hiệu hóa tổ chức");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Vô hiệu hóa tổ chức</AlertDialogTitle>
          <AlertDialogDescription>
            Bạn có chắc chắn muốn vô hiệu hóa tổ chức{" "}
            <span className="font-semibold">{tenant?.name}</span>? Tất cả người dùng thuộc
            tổ chức này sẽ mất quyền truy cập ngay lập tức.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isSubmitting}>Hủy</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleDeactivate}
            disabled={isSubmitting}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
          >
            {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Vô hiệu hóa
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
