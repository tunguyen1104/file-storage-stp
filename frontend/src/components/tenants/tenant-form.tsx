"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Button } from "@/components/ui/button";
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
import { Loader2 } from "lucide-react";

const tenantSchema = z.object({
  name: z.string().min(1, "Name is required").max(255),
  slug: z.string()
    .min(1, "Slug is required")
    .max(100)
    .regex(/^[a-z0-9-]+$/, "Slug can only contain lowercase letters, numbers, and hyphens"),
  storageQuotaGB: z.string().optional(),
});

export type TenantFormValues = z.infer<typeof tenantSchema>;

interface TenantFormProps {
  initialData?: Partial<TenantFormValues>;
  onSubmit: (values: TenantFormValues) => Promise<void>;
  isSubmitting?: boolean;
}

export function TenantForm({ initialData, onSubmit, isSubmitting }: TenantFormProps) {
  const form = useForm<TenantFormValues>({
    resolver: zodResolver(tenantSchema),
    defaultValues: {
      name: initialData?.name || "",
      slug: initialData?.slug || "",
      storageQuotaGB: initialData?.storageQuotaGB || "",
    },
  });

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6 max-w-2xl bg-card p-6 md:p-8 rounded-xl border border-border/50 shadow-sm">
        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Tenant Name</FormLabel>
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
                Unique identifier for the tenant. Lowercase letters, numbers, and hyphens only.
              </FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="storageQuotaGB"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Storage Quota (GB)</FormLabel>
              <FormControl>
                <Input type="number" placeholder="100" {...field} />
              </FormControl>
              <FormDescription>
                Leave empty for default or unlimited storage (depending on system settings).
              </FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        <Button type="submit" className="shadow-sm" disabled={isSubmitting}>
          {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          {initialData ? "Save Changes" : "Create Tenant"}
        </Button>
      </form>
    </Form>
  );
}
