"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { TenantForm, TenantFormValues } from "@/components/tenants/tenant-form";
import { createTenant } from "@/lib/api/tenants";

export default function CreateTenantPage() {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (values: TenantFormValues) => {
    setIsSubmitting(true);
    try {
      const payload = {
        name: values.name,
        slug: values.slug,
        storageQuotaBytes: values.storageQuotaGB ? Number(values.storageQuotaGB) * 1024 * 1024 * 1024 : undefined,
      };
      await createTenant(payload);
      toast.success("Tenant created successfully");
      router.push("/tenants");
      router.refresh();
    } catch (error: any) {
      toast.error(error.response?.data?.message || "Failed to create tenant");
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
          <h1 className="text-2xl font-semibold tracking-tight">Create New Tenant</h1>
          <p className="text-sm text-muted-foreground">Add a new workspace or organization.</p>
        </div>
      </div>

      <div className="rounded-lg border border-border/50 bg-card p-6">
        <TenantForm onSubmit={handleSubmit} isSubmitting={isSubmitting} />
      </div>
    </section>
  );
}
