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
import { parseBytes, convertToBytes } from "@/lib/utils/storage";
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
      toast.success("Tenant created successfully");
      form.reset();
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to create tenant");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Create New Tenant</DialogTitle>
          <DialogDescription>
            Add a new organization tenant. The slug must be unique and URL-safe.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Organization Name *</FormLabel>
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
                  <FormLabel>Slug *</FormLabel>
                  <FormControl>
                    <Input placeholder="acme-corp" {...field} />
                  </FormControl>
                  <FormDescription>
                    Unique identifier — lowercase letters, numbers, hyphens only
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
                      Storage Quota
                    </span>
                  </FormLabel>
                  <div className="flex gap-2">
                    <FormControl>
                      <Input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="e.g. 5"
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
                              <SelectValue placeholder="Unit" />
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
                  <FormDescription>Leave empty to use the system default</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => handleClose(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Create Tenant
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
        name: values.name,
        slug: values.slug,
        storageQuotaBytes: newQuotaBytes ? newQuotaBytes.toString() : "",
      });
      toast.success("Tenant updated successfully");
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to update tenant");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Edit Tenant</DialogTitle>
          <DialogDescription>
            Update tenant information. The slug must remain unique.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Organization Name</FormLabel>
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
                  <FormLabel>Slug</FormLabel>
                  <FormControl>
                    <Input placeholder="acme-corp" {...field} />
                  </FormControl>
                  <FormDescription>
                    Lowercase letters, numbers, hyphens only
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
                      Storage Quota
                    </span>
                  </FormLabel>
                  <div className="flex gap-2">
                    <FormControl>
                      <Input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="e.g. 5"
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
                              <SelectValue placeholder="Unit" />
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
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Save Changes
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
      toast.success(`Tenant "${tenant.name}" deactivated`);
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to deactivate tenant");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Deactivate Tenant</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to deactivate{" "}
            <span className="font-semibold">{tenant?.name}</span>? All users under
            this tenant will lose access immediately.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isSubmitting}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleDeactivate}
            disabled={isSubmitting}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
          >
            {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Deactivate
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
