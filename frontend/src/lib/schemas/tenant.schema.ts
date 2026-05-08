import { z } from "zod";
import { STORAGE_UNITS } from "@/lib/utils/storage";

export const createTenantSchema = z.object({
  name: z.string().min(1, "Name is required").max(255),
  slug: z
    .string()
    .min(1, "Slug is required")
    .max(100)
    .regex(
      /^[a-z0-9-]+$/,
      "Slug can only contain lowercase letters, numbers, and hyphens"
    ),
  storageQuotaValue: z.string().optional(),
  storageQuotaUnit: z.enum(STORAGE_UNITS).optional(),
});

export type CreateTenantInput = z.infer<typeof createTenantSchema>;

export const updateTenantSchema = z.object({
  name: z.string().min(1, "Name is required").max(255).optional(),
  slug: z
    .string()
    .min(1)
    .max(100)
    .regex(
      /^[a-z0-9-]+$/,
      "Slug can only contain lowercase letters, numbers, and hyphens"
    )
    .optional(),
  storageQuotaValue: z.string().optional(),
  storageQuotaUnit: z.enum(STORAGE_UNITS).optional(),
});

export type UpdateTenantInput = z.infer<typeof updateTenantSchema>;
