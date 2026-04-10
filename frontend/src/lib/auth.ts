import type { Role } from "@/lib/constants";

/**
 * Shared JWT payload interface — used by middleware and BFF proxy.
 * Must match the claims emitted by the backend JWT issuer.
 *
 * IMPORTANT: This is the single source of truth for the token shape.
 * Do NOT create duplicate interfaces elsewhere (#8).
 */
export interface DecodedToken {
  sub: string;
  userId: string;
  email: string;
  fullName: string;
  role: Role;
  tenantId: string;
  exp: number;
  iat: number;
}

/**
 * Check whether a user's role is included in a list of allowed roles.
 * Useful for UI-level role gating (e.g., conditionally rendering buttons).
 */
export function hasRole(userRole: Role, allowedRoles: Role[]): boolean {
  return allowedRoles.includes(userRole);
}

/**
 * Mask an email address for safe display (anti-phishing).
 * Prevents crafted URLs from displaying arbitrary emails.
 *
 * "john.doe@example.com" → "j*******e@example.com"
 */
export function maskEmail(email: string): string {
  const atIndex = email.indexOf("@");
  if (atIndex < 0) return "***";
  if (atIndex <= 2) return `***${email.slice(atIndex)}`;
  const local = email.slice(0, atIndex);
  const domain = email.slice(atIndex);
  return `${local[0]}${"*".repeat(local.length - 2)}${local[local.length - 1]}${domain}`;
}
