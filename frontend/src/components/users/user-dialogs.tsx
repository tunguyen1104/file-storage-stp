// src/components/users/user-dialogs.tsx
"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, HardDrive } from "lucide-react";
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
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { createUser, updateUser, deactivateUser } from "@/lib/api/users";
import {
  createUserSchema,
  updateUserSchema,
  type CreateUserInput,
  type UpdateUserInput,
} from "@/lib/schemas/user-management.schema";
import { ROLES, type Role } from "@/lib/constants";
import { TenantSelect } from "@/components/tenants/TenantSelect";
import { bytesToGbString, gbStringToBytes } from "@/lib/utils/storage";
import type { UserResponse } from "@/types/user/user";

// ─── Create User Dialog ──────────────────────────────────────────

interface CreateUserDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  isSuperAdmin?: boolean;
  onSuccess: () => void;
}

export function CreateUserDialog({
  open,
  onOpenChange,
  isSuperAdmin = false,
  onSuccess,
}: CreateUserDialogProps) {
  const form = useForm<CreateUserInput>({
    resolver: zodResolver(createUserSchema),
    defaultValues: {
      email: "",
      fullName: "",
      roleCode: undefined,
      tenantId: "",
      storageQuotaGb: "",
    },
  });

  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleClose(open: boolean) {
    if (!open) form.reset();
    onOpenChange(open);
  }

  async function onSubmit(values: CreateUserInput) {
    // Tenant is required for super admin
    if (isSuperAdmin && !values.tenantId) {
      form.setError("tenantId", { message: "Please select a tenant" });
      return;
    }
    setIsSubmitting(true);
    try {
      await createUser({
        email: values.email,
        fullName: values.fullName,
        roleCode: values.roleCode,
        tenantId: values.tenantId || undefined,
        storageQuotaBytes: values.storageQuotaGb
          ? gbStringToBytes(values.storageQuotaGb)
          : undefined,
      });
      toast.success("User created successfully");
      form.reset();
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to create user");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Create New User</DialogTitle>
          <DialogDescription>
            Add a new user to the system. Fields marked with * are required.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="fullName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Full Name *</FormLabel>
                  <FormControl>
                    <Input placeholder="John Doe" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Email *</FormLabel>
                  <FormControl>
                    <Input type="email" placeholder="john@example.com" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="roleCode"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Role *</FormLabel>
                  <Select onValueChange={field.onChange} value={field.value}>
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder="Select a role" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value={ROLES.SUPER_ADMIN}>Super Admin</SelectItem>
                      <SelectItem value={ROLES.TENANT_ADMIN}>Tenant Admin</SelectItem>
                      <SelectItem value={ROLES.MEMBER}>Member</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            {isSuperAdmin && (
              <FormField
                control={form.control}
                name="tenantId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Tenant *</FormLabel>
                    <FormControl>
                      <TenantSelect
                        value={field.value || ""}
                        onChange={field.onChange}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}

            <FormField
              control={form.control}
              name="storageQuotaGb"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    <span className="flex items-center gap-1.5">
                      <HardDrive className="h-3.5 w-3.5" />
                      Storage Quota (GB)
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      min="0.1"
                      step="0.1"
                      placeholder="e.g. 10"
                      {...field}
                      value={field.value ?? ""}
                    />
                  </FormControl>
                  <FormDescription>Leave empty to use the default quota</FormDescription>
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
                Create User
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

// ─── Edit User Dialog ────────────────────────────────────────────

interface EditUserDialogProps {
  user: UserResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function EditUserDialog({
  user,
  open,
  onOpenChange,
  onSuccess,
}: EditUserDialogProps) {
  const form = useForm<UpdateUserInput>({
    resolver: zodResolver(updateUserSchema),
    values: user
      ? {
          fullName: user.fullName,
          roleCode: user.role as Role,
          storageQuotaGb: bytesToGbString(user.storageQuotaBytes),
        }
      : undefined,
  });

  const [isSubmitting, setIsSubmitting] = useState(false);

  async function onSubmit(values: UpdateUserInput) {
    if (!user) return;
    setIsSubmitting(true);
    try {
      const newStorageBytes = values.storageQuotaGb
        ? gbStringToBytes(values.storageQuotaGb)
        : undefined;

      await updateUser(user.id, {
        fullName: values.fullName !== user.fullName ? values.fullName : undefined,
        roleCode: values.roleCode !== (user.role as Role) ? values.roleCode : undefined,
        storageQuotaBytes:
          newStorageBytes !== user.storageQuotaBytes ? newStorageBytes : undefined,
      });
      toast.success("User updated successfully");
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to update user");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Edit User</DialogTitle>
          <DialogDescription>Update user information.</DialogDescription>
        </DialogHeader>

        {/* Read-only email display — outside the form, not submitted */}
        <div className="space-y-1.5">
          <Label className="text-sm font-medium">Email</Label>
          <Input
            value={user?.email ?? ""}
            disabled
            className="bg-muted/50 text-muted-foreground"
          />
          <p className="text-xs text-muted-foreground">Email cannot be changed after creation.</p>
        </div>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="fullName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Full Name</FormLabel>
                  <FormControl>
                    <Input placeholder="John Doe" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="roleCode"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Role</FormLabel>
                  <Select onValueChange={field.onChange} value={field.value}>
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder="Select a role" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value={ROLES.SUPER_ADMIN}>Super Admin</SelectItem>
                      <SelectItem value={ROLES.TENANT_ADMIN}>Tenant Admin</SelectItem>
                      <SelectItem value={ROLES.MEMBER}>Member</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="storageQuotaGb"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    <span className="flex items-center gap-1.5">
                      <HardDrive className="h-3.5 w-3.5" />
                      Storage Quota (GB)
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      min="0.1"
                      step="0.1"
                      placeholder="e.g. 10"
                      {...field}
                      value={field.value ?? ""}
                    />
                  </FormControl>
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

// ─── Deactivate User Dialog ──────────────────────────────────────

interface DeactivateUserDialogProps {
  user: UserResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function DeactivateUserDialog({
  user,
  open,
  onOpenChange,
  onSuccess,
}: DeactivateUserDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleDeactivate() {
    if (!user) return;
    setIsSubmitting(true);
    try {
      await deactivateUser(user.id);
      toast.success(`User "${user.fullName}" deactivated`);
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to deactivate user");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Deactivate User</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to deactivate{" "}
            <span className="font-semibold">{user?.fullName}</span> ({user?.email})?
            The user will lose access immediately.
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
