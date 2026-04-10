import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import {
  generateCsrfToken,
  validateCsrfToken,
  CSRF_COOKIE_NAME,
  CSRF_HEADER_NAME,
} from "@/lib/csrf";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

/**
 * Whitelist of allowed API path prefixes.
 * Prevents SSRF by blocking requests to internal backend endpoints
 * (e.g. Spring Boot Actuator, admin APIs) that are not meant to be public.
 */
const ALLOWED_PREFIXES = [
  "/api/auth/",
  "/api/files",
  "/api/users",
  "/api/tenants",
  "/api/audit-logs",
  "/api/search",
];

/**
 * Headers that should NOT be forwarded from the backend response
 * because they are managed by the transport layer or Next.js itself.
 */
const SKIP_RESPONSE_HEADERS = new Set([
  "transfer-encoding",
  "connection",
  "keep-alive",
  "set-cookie", // handled separately via getSetCookie()
]);

const MAX_LOGOUT_RETRIES = 2;

/**
 * Ensure CSRF token cookie exists. If not, generate and set one.
 * Returns the token value for validation.
 */
async function ensureCsrfToken(
  response: NextResponse
): Promise<string> {
  const cookieStore = await cookies();
  let csrfToken = cookieStore.get(CSRF_COOKIE_NAME)?.value;

  if (!csrfToken) {
    csrfToken = generateCsrfToken();
    response.cookies.set(CSRF_COOKIE_NAME, csrfToken, {
      httpOnly: false, // Must be readable by JS to send in header
      secure: process.env.NODE_ENV === "production",
      sameSite: "strict",
      path: "/",
      maxAge: 60 * 60 * 24, // 24 hours
    });
  }

  return csrfToken;
}

/**
 * Proxy an incoming Next.js request to the real backend service.
 *
 * Security features:
 * - SSRF protection via path whitelist
 * - CSRF protection via double-submit cookie pattern
 * - Safe URL construction via URL constructor
 * - Sanitized error logging (no token/URL leaks)
 * - Logout retry with guaranteed cookie clearing
 */
export async function proxyToBackend(
  req: NextRequest
): Promise<NextResponse> {
  const pathname = req.nextUrl.pathname;
  const search = req.nextUrl.search;

  // ── SSRF protection: only allow known API prefixes ─────
  if (!ALLOWED_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
    return NextResponse.json(
      { status: "ERROR", message: "Forbidden" },
      { status: 403 }
    );
  }

  const cookieStore = await cookies();

  // ── CSRF protection for state-changing requests ─────────
  if (["POST", "PUT", "PATCH", "DELETE"].includes(req.method)) {
    const csrfCookie = cookieStore.get(CSRF_COOKIE_NAME)?.value;
    const csrfHeader = req.headers.get(CSRF_HEADER_NAME);

    if (!validateCsrfToken(csrfCookie, csrfHeader)) {
      return NextResponse.json(
        { status: "ERROR", message: "Invalid CSRF token" },
        { status: 403 }
      );
    }
  }

  const accessToken = cookieStore.get("accessToken")?.value;
  const refreshToken = cookieStore.get("refreshToken")?.value;

  // ── Build backend URL safely (prevents path traversal) ─
  const url = new URL(pathname, BACKEND_URL);
  url.search = search;
  const backendUrl = url.toString();

  // ── Build request headers ────────────────────────────────
  const headers = new Headers();

  // Forward Content-Type only when a body is expected (#18)
  const contentType = req.headers.get("content-type");
  if (contentType) {
    headers.set("Content-Type", contentType);
  } else if (!["GET", "HEAD"].includes(req.method)) {
    headers.set("Content-Type", "application/json");
  }

  // Forward Accept header
  const accept = req.headers.get("accept");
  if (accept) {
    headers.set("Accept", accept);
  }

  // Attach token as Authorization header
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  // Forward cookies to backend (needed for refresh-token rotation)
  const cookieParts: string[] = [];
  if (accessToken) cookieParts.push(`accessToken=${accessToken}`);
  if (refreshToken) cookieParts.push(`refreshToken=${refreshToken}`);
  if (cookieParts.length > 0) {
    headers.set("Cookie", cookieParts.join("; "));
  }

  // ── Build fetch options ──────────────────────────────────
  const init: RequestInit = {
    method: req.method,
    headers,
    redirect: "manual",
  };

  // Forward body for non-GET/HEAD requests
  if (!["GET", "HEAD"].includes(req.method)) {
    const body = await req.arrayBuffer();
    if (body.byteLength > 0) {
      init.body = body;
    }
  }

  // ── Execute request ──────────────────────────────────────
  try {
    const backendRes = await fetch(backendUrl, init);

    // Read response body as ArrayBuffer (works for JSON + binary)
    const responseBody = await backendRes.arrayBuffer();

    // Build Next.js response
    const response = new NextResponse(responseBody, {
      status: backendRes.status,
      statusText: backendRes.statusText,
    });

    // Forward response headers (except transport-layer and Set-Cookie)
    backendRes.headers.forEach((value, key) => {
      if (!SKIP_RESPONSE_HEADERS.has(key.toLowerCase())) {
        response.headers.set(key, value);
      }
    });

    // Forward Set-Cookie headers (handles multiple cookies correctly)
    const setCookies = backendRes.headers.getSetCookie();
    for (const cookie of setCookies) {
      response.headers.append("Set-Cookie", cookie);
    }

    // Ensure CSRF token cookie is set for subsequent requests
    await ensureCsrfToken(response);

    // Special handling: always clear cookies on logout
    if (pathname === "/api/auth/logout") {
      response.cookies.set("accessToken", "", {
        httpOnly: true,
        secure: process.env.NODE_ENV === "production",
        sameSite: "lax",
        maxAge: 0,
        path: "/",
      });
      response.cookies.set("refreshToken", "", {
        httpOnly: true,
        secure: process.env.NODE_ENV === "production",
        sameSite: "lax",
        maxAge: 0,
        path: "/",
      });
    }

    return response;
  } catch (error) {
    // ── Logout: retry backend call to invalidate refresh token (#4)
    if (pathname === "/api/auth/logout") {
      for (let attempt = 1; attempt <= MAX_LOGOUT_RETRIES; attempt++) {
        try {
          await fetch(backendUrl, {
            method: "POST",
            headers,
            redirect: "manual",
          });
          break;
        } catch {
          console.warn(
            `[BFF Proxy] Logout retry ${attempt}/${MAX_LOGOUT_RETRIES} failed`
          );
        }
      }

      // Always clear cookies even if all retries fail
      const logoutRes = NextResponse.json(
        { status: "SUCCESS", message: "Logged out" },
        { status: 200 }
      );
      logoutRes.cookies.set("accessToken", "", {
        httpOnly: true,
        secure: process.env.NODE_ENV === "production",
        sameSite: "lax",
        maxAge: 0,
        path: "/",
      });
      logoutRes.cookies.set("refreshToken", "", {
        httpOnly: true,
        secure: process.env.NODE_ENV === "production",
        sameSite: "lax",
        maxAge: 0,
        path: "/",
      });
      return logoutRes;
    }

    // Sanitized logging — never log tokens or internal URLs (#13)
    console.error(
      "[BFF Proxy] Backend request failed:",
      error instanceof Error ? error.message : "Unknown error"
    );
    return NextResponse.json(
      { status: "ERROR", message: "Service unavailable" },
      { status: 502 }
    );
  }
}
