/**
 * CSRF Protection Utilities
 *
 * Implements double-submit cookie pattern:
 * 1. Server generates a random token and sets it in a cookie (readable by JS)
 * 2. Client reads the cookie and sends it in a header on state-changing requests
 * 3. Server validates that cookie value === header value
 *
 * This works because:
 * - Attacker sites can trigger requests with cookies (auto-attached)
 * - But attacker sites CANNOT read our cookies (same-origin policy)
 * - So attacker cannot set the correct header value
 */

export const CSRF_COOKIE_NAME = "csrf-token";
export const CSRF_HEADER_NAME = "X-CSRF-Token";

/**
 * Generate a cryptographically secure random token.
 * Uses Web Crypto API (available in both browser and Edge Runtime).
 */
export function generateCsrfToken(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return Array.from(array, (byte) => byte.toString(16).padStart(2, "0")).join(
    ""
  );
}

/**
 * Read CSRF token from cookie (client-side).
 * Returns null if cookie doesn't exist.
 */
export function getCsrfToken(): string | null {
  if (typeof document === "undefined") return null;

  const cookies = document.cookie.split(";");
  for (const cookie of cookies) {
    const [name, value] = cookie.trim().split("=");
    if (name === CSRF_COOKIE_NAME) {
      return decodeURIComponent(value);
    }
  }
  return null;
}

/**
 * Validate that CSRF token from header matches cookie.
 * Used server-side in API route handlers.
 */
export function validateCsrfToken(
  cookieToken: string | undefined,
  headerToken: string | null
): boolean {
  if (!cookieToken || !headerToken) return false;
  // Use timing-safe comparison to prevent timing attacks
  if (cookieToken.length !== headerToken.length) return false;

  let result = 0;
  for (let i = 0; i < cookieToken.length; i++) {
    result |= cookieToken.charCodeAt(i) ^ headerToken.charCodeAt(i);
  }
  return result === 0;
}
