import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import {
  generateCsrfToken,
  validateCsrfToken,
  CSRF_COOKIE_NAME,
  CSRF_HEADER_NAME,
} from "@/lib/csrf";

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8081";

/**
 * Cookie name mapping: backend uses hyphens, browser uses camelCase.
 * The BFF proxy translates between them.
 */
const COOKIE_MAP = {
  "access-token": "accessToken",
  "refresh-token": "refreshToken",
} as const;

const BROWSER_COOKIE_NAMES = new Set(Object.values(COOKIE_MAP));

/**
 * Whitelist of allowed API path prefixes.
 * Prevents SSRF by blocking requests to internal backend endpoints.
 */
const ALLOWED_PREFIXES = [
  "/api/v1/auth/",
  "/api/v1/folders",
  "/api/v1/files",
  "/api/v1/uploads",
  "/api/v1/users",
  "/api/v1/tenants",
  "/api/v1/audit-logs",
  "/api/v1/search",
];

/**
 * Headers that should NOT be forwarded from the backend response.
 */
const SKIP_RESPONSE_HEADERS = new Set([
  "transfer-encoding",
  "connection",
  "keep-alive",
  "set-cookie", // handled separately via getSetCookie()
]);

const MAX_LOGOUT_RETRIES = 2;

/**
 * Parse a Set-Cookie header string and remap backend cookie names
 * (access-token, refresh-token) to camelCase (accessToken, refreshToken).
 */
function remapSetCookieHeader(setCookie: string): string {
  for (const [backendName, browserName] of Object.entries(COOKIE_MAP)) {
    // Match cookie name at start of Set-Cookie value: "access-token=..."
    if (setCookie.startsWith(`${backendName}=`)) {
      return `${browserName}=${setCookie.slice(backendName.length + 1)}`;
    }
  }
  return setCookie;
}

function clearAuthCookies(response: NextResponse) {
  for (const name of BROWSER_COOKIE_NAMES) {
    response.cookies.set(name, "", {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      maxAge: 0,
      path: "/",
    });
  }
}

/**
 * Unauthenticated auth endpoints that skip CSRF validation.
 * CSRF protection is only meaningful for authenticated sessions;
 * these endpoints have no session cookie to forge against.
 */
const CSRF_EXEMPT_PATHS = new Set([
  "/api/v1/auth/signin",
  "/api/v1/auth/refresh",
  "/api/v1/auth/forgot-password",
  "/api/v1/auth/verify-reset-token",
  "/api/v1/auth/reset-password",
  "/api/v1/auth/verify-email",
]);

/**
 * Proxy an incoming Next.js request to the real backend service.
 */
export async function proxyToBackend(
  req: NextRequest
): Promise<NextResponse> {
  const pathname = req.nextUrl.pathname;
  const search = req.nextUrl.search;

  // ── SSRF protection: only allow known API prefixes ─────
  if (!ALLOWED_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
    return NextResponse.json(
      { success: false, errors: "Forbidden" },
      { status: 403 }
    );
  }

  const cookieStore = await cookies();

  // ── CSRF protection for state-changing requests ─────────
  if (
    ["POST", "PUT", "PATCH", "DELETE"].includes(req.method) &&
    !CSRF_EXEMPT_PATHS.has(pathname)
  ) {
    const csrfCookie = cookieStore.get(CSRF_COOKIE_NAME)?.value;
    const csrfHeader = req.headers.get(CSRF_HEADER_NAME);

    if (!validateCsrfToken(csrfCookie, csrfHeader)) {
      return NextResponse.json(
        { success: false, errors: "Invalid CSRF token" },
        { status: 403 }
      );
    }
  }

  // Read browser cookies (camelCase names) and send to backend as hyphenated
  const accessToken = cookieStore.get("accessToken")?.value;
  const refreshToken = cookieStore.get("refreshToken")?.value;

  // Generate the CSRF token early so it can be forwarded to the backend
  // on the very first request (before any prior response has set the cookie).
  let csrfToken = cookieStore.get(CSRF_COOKIE_NAME)?.value;
  const isNewCsrfToken = !csrfToken;
  if (isNewCsrfToken) {
    csrfToken = generateCsrfToken();
  }

  // ── Build backend URL safely ───────────────────────────
  const url = new URL(pathname, BACKEND_URL);
  url.search = search;
  const backendUrl = url.toString();

  // ── Build request headers ────────────────────────────────
  const headers = new Headers();

  const contentType = req.headers.get("content-type");
  if (contentType && !["GET", "HEAD"].includes(req.method)) {
    headers.set("Content-Type", contentType);
  } else if (!["GET", "HEAD"].includes(req.method)) {
    headers.set("Content-Type", "application/json");
  }

  const accept = req.headers.get("accept");
  if (accept) {
    headers.set("Accept", accept);
  }

  // Forward cookies to backend using backend's expected hyphenated names
  const cookieParts: string[] = [];
  if (accessToken) cookieParts.push(`access-token=${accessToken}`);
  if (refreshToken) cookieParts.push(`refresh-token=${refreshToken}`);
  if (csrfToken) cookieParts.push(`${CSRF_COOKIE_NAME}=${csrfToken}`);
  if (cookieParts.length > 0) {
    headers.set("Cookie", cookieParts.join("; "));
  }

  // Forward CSRF token in header for backend double-submit validation
  if (["POST", "PUT", "PATCH", "DELETE"].includes(req.method) && csrfToken) {
    headers.set(CSRF_HEADER_NAME, csrfToken);
  }

  // ── Build fetch options ──────────────────────────────────
  const init: RequestInit = {
    method: req.method,
    headers,
    redirect: "manual",
  };

  if (!["GET", "HEAD"].includes(req.method)) {
    const body = await req.arrayBuffer();
    if (body.byteLength > 0) {
      init.body = body;
    }
  }

  // ── Execute request ──────────────────────────────────────
  try {
    const backendRes = await fetch(backendUrl, init);
    const responseBody = await backendRes.arrayBuffer();

    // 204 No Content (and similar) statuses cannot have a body in Next.js Response constructor
    const isNoContent = backendRes.status === 204 || backendRes.status === 205 || backendRes.status === 304;
    const bodyToPass = isNoContent ? null : responseBody;

    const response = new NextResponse(bodyToPass, {
      status: backendRes.status,
      statusText: backendRes.statusText,
    });

    // Forward response headers (except transport-layer and Set-Cookie)
    backendRes.headers.forEach((value, key) => {
      if (!SKIP_RESPONSE_HEADERS.has(key.toLowerCase())) {
        response.headers.set(key, value);
      }
    });

    // Forward Set-Cookie headers, remapping backend cookie names to browser names
    const setCookies = backendRes.headers.getSetCookie();
    for (const cookie of setCookies) {
      response.headers.append("Set-Cookie", remapSetCookieHeader(cookie));
    }

    // Use headers.append (not response.cookies.set) to avoid the ResponseCookies
    // API doing a delete-then-rewrite of ALL Set-Cookie headers, which can drop
    // the auth cookies already appended above.
    if (isNewCsrfToken && csrfToken) {
      const parts = [
        `${CSRF_COOKIE_NAME}=${csrfToken}`,
        `Path=/`,
        `SameSite=Strict`,
        `Max-Age=${60 * 60 * 24}`,
      ];
      if (process.env.NODE_ENV === "production") parts.push(`Secure`);
      response.headers.append("Set-Cookie", parts.join("; "));
    }

    // Always clear cookies on logout
    if (pathname === "/api/v1/auth/logout") {
      clearAuthCookies(response);
    }

    return response;
  } catch (error) {
    // ── Logout: retry backend call to invalidate refresh token
    if (pathname === "/api/v1/auth/logout") {
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

      const logoutRes = NextResponse.json(
        { success: true, message: "Logged out" },
        { status: 200 }
      );
      clearAuthCookies(logoutRes);
      return logoutRes;
    }

    console.error(
      "[BFF Proxy] Backend request failed:",
      error instanceof Error ? error.message : "Unknown error"
    );
    return NextResponse.json(
      { success: false, errors: "Service unavailable" },
      { status: 502 }
    );
  }
}
