// src/lib/schemas/tenant.schema.ts
import { z } from "zod";

const storageQuotaBytesField = z
  .string()
  .refine((val) => !val || (/^\d+$/.test(val) && BigInt(val) > BigInt(0)), {
    message: "Must be a positive whole number",
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
  storageQuotaBytes: storageQuotaBytesField,
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
  storageQuotaBytes: storageQuotaBytesField,
});

export type UpdateTenantInput = z.infer<typeof updateTenantSchema>;
