"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { toast } from "sonner";
import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { UserForm, UserFormValues } from "@/components/users/user-form";
import { getUserById, updateUser, listUsers, listTenantUsers } from "@/lib/api/users";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/hooks/use-auth";

export default function EditUserPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { user: currentUser } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [initialData, setInitialData] = useState<Partial<UserFormValues> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  
  const [availableRoles, setAvailableRoles] = useState<{ id: string; name: string }[]>([]);

  useEffect(() => {
    if (!id || !currentUser) return;
    
    let isMounted = true;

    const fetchData = async () => {
      try {
        const fetchMethod = currentUser.role === "SUPER_ADMIN" ? listUsers : listTenantUsers;
        const [user, usersData] = await Promise.all([
          getUserById(id),
          fetchMethod({ limit: 100 })
        ]);

        if (!isMounted) return;

        if (user) {
          setInitialData({
            fullName: user.fullName,
            email: user.email,
            roleId: typeof user.role === 'string' ? '' : user.role.id,
            storageQuotaGB: user.storageQuotaBytes ? String(user.storageQuotaBytes / (1024 * 1024 * 1024)) : "",
          });
        } else {
          toast.error("User not found");
          router.push("/users");
          return;
        }

        // Extract unique roles
        const rolesMap = new Map<string, string>();
        usersData.items.forEach((u: any) => {
          if (typeof u.role !== 'string' && u.role.id) {
            rolesMap.set(u.role.id, u.role.name);
          }
        });
        
        const roles = Array.from(rolesMap.entries()).map(([roleId, name]) => ({ id: roleId, name }));
        setAvailableRoles(roles);

      } catch (error) {
        if (isMounted) {
          toast.error("Failed to fetch user details or roles");
          router.push("/users");
        }
      } finally {
        if (isMounted) setIsLoading(false);
      }
    };

    fetchData();

    return () => {
      isMounted = false;
    };
  }, [id, router, currentUser]);

  const handleSubmit = async (values: UserFormValues) => {
    setIsSubmitting(true);
    try {
      const payload = {
        fullName: values.fullName,
        email: values.email,
        roleId: values.roleId,
        storageQuotaBytes: values.storageQuotaGB ? Number(values.storageQuotaGB) * 1024 * 1024 * 1024 : undefined,
      };
      await updateUser(id, payload);
      toast.success("User updated successfully");
      router.push("/users");
      router.refresh();
    } catch (error: any) {
      toast.error(error.response?.data?.message || "Failed to update user");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="outline" size="icon" asChild>
          <Link href="/users">
            <ChevronLeft className="h-4 w-4" />
          </Link>
        </Button>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Edit User</h1>
          <p className="text-sm text-muted-foreground">Update user details and access.</p>
        </div>
      </div>

      <div className="rounded-lg border border-border/50 bg-card p-6">
        {isLoading ? (
          <div className="space-y-6 max-w-2xl">
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-10 w-32" />
          </div>
        ) : initialData ? (
          <UserForm 
            onSubmit={handleSubmit} 
            isSubmitting={isSubmitting} 
            initialData={initialData} 
            isEditMode={true}
            availableRoles={availableRoles}
            availableTenants={[]} // Not needed in edit mode
          />
        ) : null}
      </div>
    </section>
  );
}
