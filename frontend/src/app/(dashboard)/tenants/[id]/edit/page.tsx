"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { toast } from "sonner";
import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { TenantForm, TenantFormValues } from "@/components/tenants/tenant-form";
import { getTenantById, updateTenant } from "@/lib/api/tenants";
import { Skeleton } from "@/components/ui/skeleton";

export default function EditTenantPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [initialData, setInitialData] = useState<Partial<TenantFormValues> | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    
    const fetchTenant = async () => {
      try {
        const tenant = await getTenantById(id);
        if (tenant) {
          setInitialData({
            name: tenant.name,
            slug: tenant.slug,
            storageQuotaGB: tenant.storageQuotaBytes ? String(tenant.storageQuotaBytes / (1024 * 1024 * 1024)) : "",
          });
        } else {
          toast.error("Tenant not found");
          router.push("/tenants");
        }
      } catch (error) {
        toast.error("Failed to fetch tenant details");
        router.push("/tenants");
      } finally {
        setIsLoading(false);
      }
    };

    fetchTenant();
  }, [id, router]);

  const handleSubmit = async (values: TenantFormValues) => {
    setIsSubmitting(true);
    try {
      const payload = {
        name: values.name,
        slug: values.slug,
        storageQuotaBytes: values.storageQuotaGB ? Number(values.storageQuotaGB) * 1024 * 1024 * 1024 : undefined,
      };
      await updateTenant(id, payload);
      toast.success("Tenant updated successfully");
      router.push("/tenants");
      router.refresh();
    } catch (error: any) {
      toast.error(error.response?.data?.message || "Failed to update tenant");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="outline" size="icon" asChild>
          <Link href="/tenants">
            <ChevronLeft className="h-4 w-4" />
          </Link>
        </Button>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Edit Tenant</h1>
          <p className="text-sm text-muted-foreground">Update tenant workspace settings.</p>
        </div>
      </div>

      <div className="rounded-lg border border-border/50 bg-card p-6">
        {isLoading ? (
          <div className="space-y-6 max-w-2xl">
            <div className="space-y-2">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-10 w-full" />
            </div>
            <div className="space-y-2">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-10 w-full" />
            </div>
            <div className="space-y-2">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-10 w-full" />
            </div>
            <Skeleton className="h-10 w-32" />
          </div>
        ) : initialData ? (
          <TenantForm onSubmit={handleSubmit} isSubmitting={isSubmitting} initialData={initialData} />
        ) : null}
      </div>
    </section>
  );
}
