"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { UserForm, UserFormValues } from "@/components/users/user-form";
import { createUser, listUsers, listTenantUsers } from "@/lib/api/users";
import { listTenants } from "@/lib/api/tenants";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/hooks/use-auth";

export default function CreateUserPage() {
  const router = useRouter();
  const { user } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  
  const [availableTenants, setAvailableTenants] = useState<{ id: string; name: string }[]>([]);
  const [availableRoles, setAvailableRoles] = useState<{ id: string; name: string }[]>([]);

  useEffect(() => {
    if (!user) return;
    let isMounted = true;
    
    const fetchData = async () => {
      try {
        const fetchMethod = user.role === "SUPER_ADMIN" ? listUsers : listTenantUsers;
        
        const promises: Promise<any>[] = [fetchMethod({ limit: 100 })];
        if (user.role === "SUPER_ADMIN") {
          promises.push(listTenants({ limit: 100 }));
        }

        const results = await Promise.all(promises);
        if (!isMounted) return;
        
        const usersData = results[0];
        if (user.role === "SUPER_ADMIN") {
          const tenantsData = results[1];
          setAvailableTenants(tenantsData.items.map((t: any) => ({ id: t.id, name: t.name })));
        }
        
        // Extract unique roles from existing users as a workaround
        const rolesMap = new Map<string, string>();
        usersData.items.forEach((u: any) => {
          if (typeof u.role !== 'string' && u.role.id) {
            rolesMap.set(u.role.id, u.role.name);
          }
        });
        
        const roles = Array.from(rolesMap.entries()).map(([id, name]) => ({ id, name }));
        setAvailableRoles(roles);
      } catch (error) {
        if (isMounted) {
          toast.error("Failed to load required data (tenants or roles)");
        }
      } finally {
        if (isMounted) setIsLoading(false);
      }
    };
    
    fetchData();
    
    return () => {
      isMounted = false;
    };
  }, [user]);

  const handleSubmit = async (values: UserFormValues) => {
    setIsSubmitting(true);
    try {
      const payload = {
        fullName: values.fullName,
        email: values.email,
        roleId: values.roleId,
        tenantId: values.tenantId,
        storageQuotaBytes: values.storageQuotaGB ? Number(values.storageQuotaGB) * 1024 * 1024 * 1024 : undefined,
      };
      await createUser(payload);
      toast.success("User invited successfully");
      router.push("/users");
      router.refresh();
    } catch (error: any) {
      toast.error(error.response?.data?.message || "Failed to invite user");
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
          <h1 className="text-2xl font-semibold tracking-tight">Invite New User</h1>
          <p className="text-sm text-muted-foreground">Provision a new user account.</p>
        </div>
      </div>

      <div className="rounded-lg border border-border/50 bg-card p-6">
        {isLoading ? (
          <div className="space-y-6 max-w-2xl">
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-10 w-32" />
          </div>
        ) : (
          <UserForm 
            onSubmit={handleSubmit} 
            isSubmitting={isSubmitting} 
            availableTenants={availableTenants}
            availableRoles={availableRoles}
            hideTenantSelect={user?.role !== "SUPER_ADMIN"}
          />
        )}
      </div>
    </section>
  );
}
