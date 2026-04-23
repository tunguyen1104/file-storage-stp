// src/lib/schemas/user-management.schema.ts
import { z } from "zod";
import { ROLES } from "@/lib/constants";

const roleCodes = [ROLES.SUPER_ADMIN, ROLES.TENANT_ADMIN, ROLES.MEMBER] as const;

const storageQuotaGbField = z
  .string()
  .refine((val) => !val || (!isNaN(parseFloat(val)) && parseFloat(val) > 0), {
    message: "Must be a positive number",
  })
  .optional();

export const createUserSchema = z.object({
  email: z.string().email("Invalid email address"),
  fullName: z.string().min(1, "Full name is required").max(255),
  roleCode: z.enum(roleCodes, { message: "Please select a role" }),
  tenantId: z.string().optional(),
  storageQuotaGb: storageQuotaGbField,
});

export type CreateUserInput = z.infer<typeof createUserSchema>;

// email is intentionally excluded — it cannot be changed after creation
export const updateUserSchema = z.object({
  fullName: z.string().min(1, "Full name is required").max(255).optional(),
  roleCode: z.enum(roleCodes, { message: "Please select a role" }).optional(),
  storageQuotaGb: storageQuotaGbField,
});

export type UpdateUserInput = z.infer<typeof updateUserSchema>;
