"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import useSWR from "swr";
import { useRouter } from "next/navigation";
import { Camera, KeyRound, Loader2 } from "lucide-react";
import { toast } from "sonner";

import { useAuth } from "@/hooks/use-auth";
import { getUserById, updateUser } from "@/lib/api/users";
import { toAppError } from "@/lib/api/core/api-error";
import type { Role } from "@/lib/constants";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Progress } from "@/components/ui/progress";
import { ChangePasswordDialog } from "@/components/profile/ChangePasswordDialog";

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

const profileSchema = z.object({
  fullName: z.string().min(1, "Full name is required"),
});

type ProfileFormValues = z.infer<typeof profileSchema>;

export default function ProfilePage() {
  const router = useRouter();
  const { user } = useAuth();
  const [isPasswordDialogOpen, setIsPasswordDialogOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  // Fetch data
  const { data: profile, mutate: mutateProfile, isLoading: isLoadingProfile } = useSWR(
    user?.id ? ["userProfile", user.id] : null,
    () => getUserById(user!.id)
  );

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      fullName: "",
    },
  });

  // Reset form when profile data loads
  useEffect(() => {
    if (profile) {
      reset({
        fullName: profile.fullName,
      });
    }
  }, [profile, reset]);

  async function onSubmit(data: ProfileFormValues) {
    if (!user?.id || !profile?.role) return;
    setIsSaving(true);
    try {
      await updateUser(user.id, {
        ...data,
        roleCode: profile.role as Role,
      });
      toast.success("Profile updated successfully");
      await mutateProfile();
    } catch (error) {
      toast.error(toAppError(error).message);
    } finally {
      setIsSaving(false);
    }
  }

  function handleCancel() {
    if (isDirty && profile) {
      reset({
        fullName: profile.fullName,
      });
    }
    router.back();
  }

  if (isLoadingProfile) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-gray-400" />
      </div>
    );
  }

  const storageUsedBytes = profile?.storageUsedBytes ?? 0;
  const storageQuotaBytes = profile?.storageQuotaBytes ?? 0;
  const usagePercent = storageQuotaBytes > 0 ? (storageUsedBytes / storageQuotaBytes) * 100 : 0;

  const storageUsedStr = formatBytes(storageUsedBytes);
  const storageQuotaStr = formatBytes(storageQuotaBytes);

  return (
    <div className="container mx-auto p-6 max-w-4xl h-full overflow-y-auto">
      <form onSubmit={handleSubmit(onSubmit)} className="bg-white rounded-xl border border-gray-200 shadow-sm">
        
        {/* Header */}
        <div className="p-8 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="relative">
              <Avatar className="h-[72px] w-[72px] border border-gray-100 shadow-sm">
                <AvatarImage src={`https://api.dicebear.com/7.x/avataaars/svg?seed=${profile?.email}`} />
                <AvatarFallback>{profile?.fullName.charAt(0)}</AvatarFallback>
              </Avatar>
              <div 
                className="absolute bottom-0 right-0 bg-[#7C3AED] text-white p-1.5 rounded-full cursor-not-allowed shadow-sm border-2 border-white flex items-center justify-center" 
                title="Avatar upload coming soon"
              >
                <Camera className="h-3.5 w-3.5" />
              </div>
            </div>
            <div className="flex flex-col">
              <h1 className="text-xl font-bold text-gray-900 leading-tight">Thông tin người dùng</h1>
              <p className="text-sm text-gray-500 mt-1">Cập nhật và quản lý hồ sơ tài khoản</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Button 
              type="button" 
              variant="outline" 
              className="text-gray-600 font-medium"
              onClick={handleCancel}
              disabled={isSaving}
            >
              Cancel
            </Button>
            <Button 
              type="submit" 
              className="bg-[#7C3AED] hover:bg-[#6D28D9] text-white font-medium"
              disabled={!isDirty || isSaving}
            >
              {isSaving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Save Changes
            </Button>
          </div>
        </div>

        <div className="border-t border-gray-100 p-8 space-y-10">
          {/* Form Fields */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-6">
            <div className="space-y-2">
              <Label htmlFor="fullName" className="text-sm font-semibold text-gray-700">Full Name</Label>
              <Input 
                id="fullName" 
                {...register("fullName")} 
                className={errors.fullName ? "border-red-500" : ""}
                placeholder="Enter full name"
              />
              {errors.fullName && <p className="text-xs text-red-500">{errors.fullName.message}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="email" className="text-sm font-semibold text-gray-700">Email Address</Label>
              <Input 
                id="email" 
                value={profile?.email || ""} 
                disabled 
                className="bg-gray-50/80 text-gray-500 cursor-not-allowed"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="role" className="text-sm font-semibold text-gray-700">Role</Label>
              <div className="relative">
                <select 
                  id="role" 
                  value={profile?.role || ""} 
                  disabled 
                  className="flex h-10 w-full rounded-md border border-input bg-gray-50/80 px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 text-gray-500 appearance-none"
                >
                  <option value={profile?.role}>{profile?.role?.replace("_", " ")}</option>
                </select>
                <div className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-gray-400">
                  <svg width="15" height="15" viewBox="0 0 15 15" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M4.93179 5.43179C4.75605 5.60753 4.75605 5.89245 4.93179 6.06819L7.43179 8.56819C7.60753 8.74393 7.89245 8.74393 8.06819 8.56819L10.5682 6.06819C10.7439 5.89245 10.7439 5.60753 10.5682 5.43179C10.3924 5.25605 10.1075 5.25605 9.93179 5.43179L7.5 7.86358L5.06819 5.43179C4.89245 5.25605 4.60753 5.25605 4.43179 5.43179Z" fill="currentColor" fillRule="evenodd" clipRule="evenodd"></path>
                  </svg>
                </div>
              </div>
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="tenant" className="text-sm font-semibold text-gray-700">Tenant</Label>
              <Input 
                id="tenant" 
                value={profile?.tenantName || "N/A"} 
                disabled 
                className="bg-gray-50/80 text-gray-500 cursor-not-allowed"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="password" className="text-sm font-semibold text-gray-700">Password</Label>
              <div className="flex items-center gap-3">
                <Input 
                  id="password" 
                  value="••••••••" 
                  disabled 
                  className="bg-gray-50/80 text-gray-500 font-mono tracking-widest cursor-not-allowed"
                />
                <Button 
                  type="button" 
                  variant="secondary" 
                  className="shrink-0 bg-[#F3E8FF] text-[#7C3AED] hover:bg-[#E9D5FF] gap-2 border-0 font-medium"
                  onClick={() => setIsPasswordDialogOpen(true)}
                >
                  <KeyRound className="h-4 w-4" />
                  Change Password
                </Button>
              </div>
            </div>
          </div>

          {/* Storage Management Block */}
          <div className="bg-[#FAFAFA] rounded-xl p-6 border border-gray-100">
            <h3 className="font-bold text-gray-900 mb-6 text-[15px]">Storage Management</h3>
            
            <div className="grid grid-cols-1 md:grid-cols-12 gap-8 items-end">
              <div className="md:col-span-7 space-y-2.5">
                <div className="flex justify-between items-center text-sm font-semibold">
                  <span className="text-gray-500">Usage Progress</span>
                  <span className="text-[#7C3AED]">{usagePercent.toFixed(0)}% Used</span>
                </div>
                <Progress value={usagePercent} className="h-2.5 bg-gray-200" indicatorClassName="bg-[#7C3AED]" />
              </div>

              <div className="md:col-span-5 flex items-end justify-between md:justify-start md:gap-10">
                <div className="space-y-1.5">
                  <p className="text-[11px] uppercase tracking-wider font-bold text-gray-500">Storage Used</p>
                  <p className="text-xl font-extrabold text-gray-900">{storageUsedStr}</p>
                </div>
                <div className="space-y-1.5">
                  <p className="text-[11px] uppercase tracking-wider font-bold text-gray-500">Storage Quota</p>
                  <p className="text-xl font-extrabold text-gray-900">{storageQuotaStr}</p>
                </div>
              </div>
            </div>
          </div>

        </div>
      </form>

      {user?.id && (
        <ChangePasswordDialog 
          userId={user.id} 
          open={isPasswordDialogOpen} 
          onOpenChange={setIsPasswordDialogOpen} 
        />
      )}
    </div>
  );
}
