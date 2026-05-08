import { z } from "zod";
import { ROLES } from "@/lib/constants";
import { STORAGE_UNITS } from "@/lib/utils/storage";

const roleCodes = [ROLES.SUPER_ADMIN, ROLES.TENANT_ADMIN, ROLES.MEMBER] as const;

export const createUserSchema = z.object({
  email: z.string().email("Invalid email address"),
  fullName: z.string().min(1, "Full name is required").max(255),
  roleCode: z.enum(roleCodes, { message: "Please select a role" }),
  tenantId: z.string().optional(),
  storageQuotaValue: z.string().optional(),
  storageQuotaUnit: z.enum(STORAGE_UNITS).optional(),
});

export type CreateUserInput = z.infer<typeof createUserSchema>;

// email is intentionally excluded — it cannot be changed after creation
export const updateUserSchema = z.object({
  fullName: z.string().min(1, "Full name is required").max(255).optional(),
  roleCode: z.enum(roleCodes, { message: "Please select a role" }).optional(),
  storageQuotaValue: z.string().optional(),
  storageQuotaUnit: z.enum(STORAGE_UNITS).optional(),
});

export type UpdateUserInput = z.infer<typeof updateUserSchema>;
