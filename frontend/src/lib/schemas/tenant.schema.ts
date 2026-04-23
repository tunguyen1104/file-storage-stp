// src/lib/schemas/tenant.schema.ts
import { z } from "zod";

const storageQuotaGbField = z
  .string()
  .refine((val) => !val || (!isNaN(parseFloat(val)) && parseFloat(val) > 0), {
    message: "Must be a positive number",
  })
  .optional();

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
  storageQuotaGb: storageQuotaGbField,
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
  storageQuotaGb: storageQuotaGbField,
});

export type UpdateTenantInput = z.infer<typeof updateTenantSchema>;
