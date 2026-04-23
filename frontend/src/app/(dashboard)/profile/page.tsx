"use client";

import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import {
  Loader2,
  Mail,
  Shield,
  Building2,
  HardDrive,
  KeyRound,
  Pencil,
  Check,
  X,
} from "lucide-react";

import { useAuth } from "@/hooks/use-auth";
import { updateUser, changeUserPassword, getUserStorage, getUserById } from "@/lib/api/users";
import { getTenantById, updateTenant } from "@/lib/api/tenants";
import type { UserStorage } from "@/types/user/user";
import type { TenantItem } from "@/types/tenant/tenant";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Progress } from "@/components/ui/progress";
import { Separator } from "@/components/ui/separator";

// ─── Schemas ───────────────────────────────────────────────────────────────────

const profileSchema = z.object({
  fullName: z.string().min(1, "Full name is required"),
  email: z.string().email("Invalid email address"),
});
type ProfileInput = z.infer<typeof profileSchema>;

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, "Current password is required"),
    newPassword: z.string().min(8, "At least 8 characters"),
    confirmPassword: z.string().min(1, "Please confirm your password"),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });
type PasswordInput = z.infer<typeof passwordSchema>;

const tenantSchema = z.object({
  name: z.string().min(1, "Tenant name is required"),
  slug: z.string().min(1, "Slug is required").regex(/^[a-z0-9-]+$/, "Lowercase letters, numbers, hyphens only"),
  storageQuotaBytes: z
    .union([z.coerce.number().min(0), z.literal("")])
    .optional()
    .transform((v) => (v === "" || v == null ? undefined : Number(v))),
});
type TenantInput = z.infer<typeof tenantSchema>;

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatBytes(bytes: number) {
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(1)} GB`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(1)} MB`;
  if (bytes >= 1e3) return `${(bytes / 1e3).toFixed(1)} KB`;
  return `${bytes} B`;
}

// ─── Password Change Card ────────────────────────────────────────────────────

function PasswordCard({ userId, isSelf }: { userId: string; isSelf: boolean }) {
  const [open, setOpen] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<PasswordInput>({ resolver: zodResolver(passwordSchema) });

  const onSubmit = async (data: PasswordInput) => {
    try {
      await changeUserPassword(userId, {
        currentPassword: isSelf ? data.currentPassword : undefined,
        newPassword: data.newPassword,
        confirmPassword: data.confirmPassword,
      });
      toast.success("Password updated successfully");
      reset();
      setOpen(false);
    } catch {
      toast.error("Failed to update password");
    }
  };

  return (
    <Card className="border-border/50 shadow-sm">
      <CardHeader className="border-b border-border/50 pb-4">
        <div className="flex items-center justify-between">
          <div>
            <CardTitle>Security</CardTitle>
            <CardDescription>Manage your account password</CardDescription>
          </div>
          <Button
            variant="outline"
            size="sm"
            className="gap-2"
            onClick={() => setOpen((p) => !p)}
          >
            <KeyRound className="h-4 w-4" />
            {open ? "Cancel" : "Change Password"}
          </Button>
        </div>
      </CardHeader>
      {open && (
        <CardContent className="pt-6">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {isSelf && (
              <div className="space-y-1.5">
                <Label>Current Password</Label>
                <Input type="password" {...register("currentPassword")} />
                {errors.currentPassword && (
                  <p className="text-xs text-destructive">{errors.currentPassword.message}</p>
                )}
              </div>
            )}
            <div className="space-y-1.5">
              <Label>New Password</Label>
              <Input type="password" {...register("newPassword")} />
              {errors.newPassword && (
                <p className="text-xs text-destructive">{errors.newPassword.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label>Confirm New Password</Label>
              <Input type="password" {...register("confirmPassword")} />
              {errors.confirmPassword && (
                <p className="text-xs text-destructive">{errors.confirmPassword.message}</p>
              )}
            </div>
            <Button type="submit" disabled={isSubmitting} className="w-full shadow-sm">
              {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Update Password
            </Button>
          </form>
        </CardContent>
      )}
    </Card>
  );
}

// ─── Tenant Settings Card ─────────────────────────────────────────────────────

function TenantSettingsCard({
  tenant,
  onSaved,
}: {
  tenant: TenantItem;
  onSaved: (updated: TenantItem) => void;
}) {
  const [isEditing, setIsEditing] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<TenantInput>({
    resolver: zodResolver(tenantSchema) as never,
    defaultValues: {
      name: tenant.name,
      slug: tenant.slug,
      storageQuotaBytes: tenant.storageQuotaBytes,
    },
  });

  const startEditing = () => {
    reset({ name: tenant.name, slug: tenant.slug, storageQuotaBytes: tenant.storageQuotaBytes });
    setIsEditing(true);
  };

  const onSubmit = async (data: TenantInput) => {
    try {
      const updated = await updateTenant(tenant.id, {
        name: data.name,
        slug: data.slug,
        storageQuotaBytes: data.storageQuotaBytes,
      });
      toast.success("Tenant updated successfully");
      setIsEditing(false);
      if (updated) onSaved(updated);
    } catch {
      toast.error("Failed to update tenant");
    }
  };

  const field = (
    label: string,
    name: keyof TenantInput,
    placeholder?: string,
    type: string = "text"
  ) => (
    <div className="space-y-1.5">
      <Label className="text-xs font-medium text-muted-foreground">{label}</Label>
      {isEditing ? (
        <>
          <Input
            type={type}
            placeholder={placeholder}
            {...register(name)}
            className="h-9"
          />
          {errors[name] && (
            <p className="text-xs text-destructive">{errors[name]?.message}</p>
          )}
        </>
      ) : (
        <p className="text-sm font-semibold text-card-foreground">
          {name === "storageQuotaBytes"
            ? tenant.storageQuotaBytes != null
              ? formatBytes(tenant.storageQuotaBytes)
              : "—"
            : (tenant[name as "name" | "slug"] as string)}
        </p>
      )}
    </div>
  );

  return (
    <Card className="border-border/50 shadow-sm">
      <CardHeader className="border-b border-border/50 pb-4">
        <div className="flex items-center justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Building2 className="h-4 w-4 text-primary" />
              Tenant Settings
            </CardTitle>
            <CardDescription>Manage your organization's configuration</CardDescription>
          </div>
          {!isEditing && (
            <Button variant="outline" size="sm" className="gap-2" onClick={startEditing}>
              <Pencil className="h-4 w-4" />
              Edit Tenant
            </Button>
          )}
        </div>
      </CardHeader>
      <CardContent className="pt-6">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
          <div className="grid gap-5 sm:grid-cols-2">
            {field("Tenant Name", "name", "Acme Corp")}
            {field("Slug", "slug", "acme-corp")}
          </div>
          {field("Storage Quota (bytes)", "storageQuotaBytes", "e.g. 10737418240", "number")}

          {isEditing && (
            <>
              <Separator />
              <div className="flex gap-2 justify-end">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="gap-1.5"
                  onClick={() => setIsEditing(false)}
                >
                  <X className="h-4 w-4" /> Cancel
                </Button>
                <Button
                  type="submit"
                  size="sm"
                  disabled={isSubmitting || !isDirty}
                  className="gap-1.5 shadow-sm"
                >
                  {isSubmitting ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Check className="h-4 w-4" />
                  )}
                  Save Changes
                </Button>
              </div>
            </>
          )}
        </form>
      </CardContent>
    </Card>
  );
}

// ─── Main Page ────────────────────────────────────────────────────────────────

export default function ProfilePage() {
  const { user, isLoading, mutateUser } = useAuth();
  const [storage, setStorage] = useState<UserStorage | null>(null);
  const [tenant, setTenant] = useState<TenantItem | null>(null);
  const [tenantName, setTenantName] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);

  // Seed tenantName: prefer auth context → own user profile → tenants API (SUPER_ADMIN only)
  useEffect(() => {
    if (!user?.id) return;
    // If /auth/me already provides tenantName, use it immediately
    if (user.tenantName) {
      setTenantName(user.tenantName);
      return;
    }
    // Otherwise fetch own user profile which includes tenantName for all roles
    getUserById(user.id)
      .then((u) => { if (u?.tenantName) setTenantName(u.tenantName); })
      .catch(() => {});
  }, [user?.id, user?.tenantName]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ProfileInput>({ resolver: zodResolver(profileSchema) });

  // Fetch storage info
  useEffect(() => {
    if (!user?.id) return;
    getUserStorage(user.id)
      .then((s) => { if (s) setStorage(s); })
      .catch(() => { /* storage optional */ });
  }, [user?.id]);

  // Fetch full tenant object for admins who can edit it (SUPER_ADMIN + TENANT_ADMIN)
  // Note: getTenantById may be restricted to SUPER_ADMIN only on some backends,
  // so tenantName is already seeded above via getUserById.
  useEffect(() => {
    if (!user?.tenantId) return;
    if (user.role !== "SUPER_ADMIN" && user.role !== "TENANT_ADMIN") return;
    getTenantById(user.tenantId)
      .then((t) => {
        if (t) {
          setTenant(t);
          setTenantName(t.name); // authoritative name from tenant record
        }
      })
      .catch(() => { /* ignore permission errors for non-super-admins */ });
  }, [user?.tenantId, user?.role]);

  const startEditing = useCallback(() => {
    if (!user) return;
    reset({ fullName: user.fullName, email: user.email });
    setIsEditing(true);
  }, [user, reset]);

  const cancelEditing = useCallback(() => {
    setIsEditing(false);
  }, []);

  const onSubmit = async (data: ProfileInput) => {
    if (!user) return;
    try {
      await updateUser(user.id, { fullName: data.fullName, email: data.email });
      await mutateUser();
      toast.success("Profile updated successfully");
      setIsEditing(false);
    } catch {
      toast.error("Failed to update profile");
    }
  };

  // ── Loading ──
  if (isLoading) {
    return (
      <div className="space-y-6 max-w-4xl mx-auto mt-6">
        <Skeleton className="h-52 w-full rounded-xl" />
        <div className="grid gap-6 md:grid-cols-2">
          <Skeleton className="h-64 w-full rounded-xl" />
          <Skeleton className="h-64 w-full rounded-xl" />
        </div>
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    );
  }

  if (!user) return null;

  const initials = (user.fullName || user.name)
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .substring(0, 2);

  const canEditProfile = true; // all roles can edit their own profile info
  const canEditTenant = user.role === "SUPER_ADMIN" || user.role === "TENANT_ADMIN";
  const hasTenant = !!user.tenantId;

  return (
    <div className="space-y-6 max-w-4xl mx-auto mt-6 pb-12">
      {/* ── Hero Card ── */}
      <Card className="border-border/50 shadow-sm overflow-hidden">
        <div className="h-28 w-full bg-gradient-to-br from-primary/60 via-primary/40 to-primary/10" />
        <div className="px-6 pb-6 relative">
          <div className="absolute -top-12 left-6">
            <Avatar className="h-24 w-24 border-4 border-card shadow-md">
              <AvatarFallback className="bg-primary/20 text-primary text-2xl font-bold">
                {initials}
              </AvatarFallback>
            </Avatar>
          </div>
          <div className="mt-14 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold text-card-foreground">
                {user.fullName || user.name}
              </h1>
              <p className="text-muted-foreground text-sm">{user.email}</p>
            </div>
            <div className="flex items-center gap-3">
              <Badge
                variant="outline"
                className="w-fit bg-primary/10 text-primary border-primary/20 px-3 py-1 text-sm uppercase tracking-wider"
              >
                {user.role.replace(/_/g, " ")}
              </Badge>
              {canEditProfile && !isEditing && (
                <Button size="sm" variant="outline" className="gap-2" onClick={startEditing}>
                  <Pencil className="h-4 w-4" />
                  Edit Profile
                </Button>
              )}
            </div>
          </div>
        </div>
      </Card>

      {/* ── Account Info + Organization ── */}
      <div className="grid gap-6 md:grid-cols-2">
        {/* Account Info Card */}
        <Card className="border-border/50 shadow-sm">
          <CardHeader className="border-b border-border/50 pb-4">
            <CardTitle>Account Information</CardTitle>
            <CardDescription>Your personal account details</CardDescription>
          </CardHeader>
          <CardContent className="pt-6">
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
              {/* Full Name */}
              <div className="flex items-start gap-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted border border-border/50">
                  <span className="text-muted-foreground font-bold text-sm">N</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-muted-foreground mb-1">Full Name</p>
                  {isEditing ? (
                    <div>
                      <Input {...register("fullName")} className="h-9" />
                      {errors.fullName && (
                        <p className="text-xs text-destructive mt-1">{errors.fullName.message}</p>
                      )}
                    </div>
                  ) : (
                    <p className="text-sm font-semibold text-card-foreground">{user.fullName || user.name}</p>
                  )}
                </div>
              </div>

              {/* Email */}
              <div className="flex items-start gap-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted border border-border/50">
                  <Mail className="h-5 w-5 text-muted-foreground" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-muted-foreground mb-1">Email Address</p>
                  {isEditing ? (
                    <div>
                      <Input type="email" {...register("email")} className="h-9" />
                      {errors.email && (
                        <p className="text-xs text-destructive mt-1">{errors.email.message}</p>
                      )}
                    </div>
                  ) : (
                    <p className="text-sm font-semibold text-card-foreground">{user.email}</p>
                  )}
                </div>
              </div>

              {/* Role */}
              <div className="flex items-center gap-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted border border-border/50">
                  <Shield className="h-5 w-5 text-muted-foreground" />
                </div>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">Security Role</p>
                  <p className="text-sm font-semibold text-card-foreground">
                    {user.role.replace(/_/g, " ")}
                  </p>
                </div>
              </div>

              {isEditing && (
                <>
                  <Separator />
                  <div className="flex gap-2 justify-end">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="gap-1.5"
                      onClick={cancelEditing}
                    >
                      <X className="h-4 w-4" /> Cancel
                    </Button>
                    <Button type="submit" size="sm" disabled={isSubmitting} className="gap-1.5 shadow-sm">
                      {isSubmitting ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <Check className="h-4 w-4" />
                      )}
                      Save Changes
                    </Button>
                  </div>
                </>
              )}
            </form>
          </CardContent>
        </Card>

        {/* Organization Overview Card */}
        <Card className="border-border/50 shadow-sm">
          <CardHeader className="border-b border-border/50 pb-4">
            <CardTitle>Organization</CardTitle>
            <CardDescription>Workspace &amp; storage information</CardDescription>
          </CardHeader>
          <CardContent className="pt-6 space-y-5">
            {hasTenant && (
              <div className="flex items-center gap-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted border border-border/50">
                  <Building2 className="h-5 w-5 text-muted-foreground" />
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-medium text-muted-foreground mb-1">Tenant</p>
                  <p className="text-sm font-semibold text-card-foreground truncate">
                    {tenantName ?? user.tenantId}
                  </p>
                </div>
              </div>
            )}
            {!hasTenant && (
              <div className="flex items-center gap-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted border border-border/50">
                  <Building2 className="h-5 w-5 text-muted-foreground" />
                </div>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">Tenant</p>
                  <p className="text-sm font-semibold text-card-foreground">Global System</p>
                </div>
              </div>
            )}

            <div className="flex items-center gap-4">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted border border-border/50">
                <HardDrive className="h-5 w-5 text-muted-foreground" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-medium text-muted-foreground mb-1">Storage Usage</p>
                {storage ? (
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-semibold text-card-foreground">
                        {formatBytes(storage.storageUsedBytes)}
                      </span>
                      <span className="text-muted-foreground text-xs">
                        of {formatBytes(storage.storageQuotaBytes)}
                      </span>
                    </div>
                    <Progress value={storage.usagePercent} className="h-2" />
                    <p className="text-xs text-muted-foreground">
                      {storage.usagePercent.toFixed(1)}% used
                    </p>
                  </div>
                ) : (
                  <p className="text-sm font-semibold text-card-foreground">
                    Unlimited{" "}
                    <span className="text-xs font-normal text-muted-foreground">(Default)</span>
                  </p>
                )}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* ── Tenant Settings (SUPER_ADMIN + TENANT_ADMIN only) ── */}
      {canEditTenant && tenant && (
        <TenantSettingsCard
          tenant={tenant}
          onSaved={(updated) => {
            setTenant(updated);
            setTenantName(updated.name);
          }}
        />
      )}

      {/* ── Security / Password ── */}
      <PasswordCard userId={user.id} isSelf />
    </div>
  );
}
