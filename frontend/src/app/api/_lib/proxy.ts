import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import {
  generateCsrfToken,
  validateCsrfToken,
  CSRF_COOKIE_NAME,
  CSRF_HEADER_NAME,
} from "@/lib/csrf";

const BACKEND_URL =
  process.env.BACKEND_URL ??
  process.env.NEXT_PUBLIC_BACKEND_URL ??
  "http://localhost:8081";

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

const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

const AUTH_COOKIE_NAMES = ["access-token", "refresh-token"] as const;

/**
 * Headers that should NOT be forwarded from the backend response.
 */
const SKIP_RESPONSE_HEADERS = new Set([
  "transfer-encoding",
  "connection",
  "keep-alive",
  "set-cookie", // handled separately via getSetCookie()
]);

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

const REFRESH_PATH = "/api/v1/auth/refresh";
const LOGOUT_PATH = "/api/v1/auth/logout";

const MAX_LOGOUT_RETRIES = 2;

function clearAuthCookies(response: NextResponse) {
  for (const name of AUTH_COOKIE_NAMES) {
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
 * Call the backend refresh endpoint directly and return the new access token value
 * + full Set-Cookie headers so the proxy response can forward all cookie updates
 * back to the browser (with backend-set attributes intact).
 *
 * Returns null if the refresh failed for any reason — caller should fall through
 * (forward the original request without auth and let the backend reject it).
 */
async function refreshTokensViaBackend(
  refreshToken: string,
  csrfToken: string | undefined
): Promise<{ accessToken: string; setCookies: string[] } | null> {
  try {
    const url = new URL(REFRESH_PATH, BACKEND_URL).toString();

    const cookieParts = [`refresh-token=${refreshToken}`];
    if (csrfToken) cookieParts.push(`${CSRF_COOKIE_NAME}=${csrfToken}`);

    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Cookie: cookieParts.join("; "),
    };
    if (csrfToken) headers[CSRF_HEADER_NAME] = csrfToken;

    const res = await fetch(url, { method: "POST", headers });
    if (!res.ok) return null;

    const setCookies = res.headers.getSetCookie();
    const accessTokenCookie = setCookies.find((c) =>
      c.startsWith("access-token=")
    );
    if (!accessTokenCookie) return null;

    const accessToken = accessTokenCookie.split(";")[0].split("=")[1];
    return accessToken ? { accessToken, setCookies } : null;
  } catch {
    return null;
  }
}

/**
 * Proxy an incoming Next.js request to the real backend service.
 */
export async function proxyToBackend(req: NextRequest): Promise<NextResponse> {
  const pathname = req.nextUrl.pathname;
  const search = req.nextUrl.search;

  if (!ALLOWED_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
    return NextResponse.json(
      { success: false, errors: "Forbidden" },
      { status: 403 }
    );
  }

  const cookieStore = await cookies();

  if (MUTATING_METHODS.has(req.method) && !CSRF_EXEMPT_PATHS.has(pathname)) {
    const csrfCookie = cookieStore.get(CSRF_COOKIE_NAME)?.value;
    const csrfHeader = req.headers.get(CSRF_HEADER_NAME);

    if (!validateCsrfToken(csrfCookie, csrfHeader)) {
      return NextResponse.json(
        { success: false, errors: "Invalid CSRF token" },
        { status: 403 }
      );
    }
  }

  let accessToken = cookieStore.get("access-token")?.value;
  let refreshToken = cookieStore.get("refresh-token")?.value;

  // Generate the CSRF token early so it can be forwarded to the backend
  // on the very first request (before any prior response has set the cookie).
  let csrfToken = cookieStore.get(CSRF_COOKIE_NAME)?.value;
  const isNewCsrfToken = !csrfToken;
  if (isNewCsrfToken) {
    csrfToken = generateCsrfToken();
  }

  // Silent refresh: if the browser has no access-token cookie for a protected
  // endpoint but still has a refresh-token, fetch a fresh access token here so
  // the original request can succeed with valid credentials. The new tokens are
  // forwarded back to the browser via Set-Cookie on the final response.
  let refreshedSetCookies: string[] = [];
  const isProtectedEndpoint =
    !CSRF_EXEMPT_PATHS.has(pathname) && pathname !== REFRESH_PATH;
  if (!accessToken && refreshToken && isProtectedEndpoint) {
    const refreshed = await refreshTokensViaBackend(refreshToken, csrfToken);
    if (refreshed) {
      accessToken = refreshed.accessToken;
      refreshedSetCookies = refreshed.setCookies;

      // If the backend rotated the refresh token, use the new one for this request too
      const rotatedRefreshCookie = refreshed.setCookies.find((c) =>
        c.startsWith("refresh-token=")
      );
      if (rotatedRefreshCookie) {
        refreshToken = rotatedRefreshCookie.split(";")[0].split("=")[1];
      }
    }
  }

  const url = new URL(pathname, BACKEND_URL);
  url.search = search;

  const headers = new Headers();

  if (MUTATING_METHODS.has(req.method)) {
    headers.set(
      "Content-Type",
      req.headers.get("content-type") ?? "application/json"
    );
  }

  const accept = req.headers.get("accept");
  if (accept) {
    headers.set("Accept", accept);
  }

  // Forward cookies to backend using the backend's expected hyphenated names
  const cookieParts: string[] = [];
  if (accessToken) cookieParts.push(`access-token=${accessToken}`);
  if (refreshToken) cookieParts.push(`refresh-token=${refreshToken}`);
  if (csrfToken) cookieParts.push(`${CSRF_COOKIE_NAME}=${csrfToken}`);
  if (cookieParts.length > 0) {
    headers.set("Cookie", cookieParts.join("; "));
  }

  if (MUTATING_METHODS.has(req.method) && csrfToken) {
    headers.set(CSRF_HEADER_NAME, csrfToken);
  }

  const init: RequestInit = {
    method: req.method,
    headers,
    redirect: "manual",
  };

  if (MUTATING_METHODS.has(req.method)) {
    const body = await req.arrayBuffer();
    if (body.byteLength > 0) {
      init.body = body;
    }
  }

  try {
    const backendRes = await fetch(url.toString(), init);

    // Streaming branch: pipe text/event-stream (SSE) responses straight through
    // instead of buffering — buffering would defeat SSE entirely.
    const contentType = backendRes.headers.get("content-type") ?? "";
    if (contentType.startsWith("text/event-stream")) {
      const response = new NextResponse(backendRes.body, {
        status: backendRes.status,
        statusText: backendRes.statusText,
      });

      backendRes.headers.forEach((value, key) => {
        if (!SKIP_RESPONSE_HEADERS.has(key.toLowerCase())) {
          response.headers.set(key, value);
        }
      });

      for (const cookie of backendRes.headers.getSetCookie()) {
        response.headers.append("Set-Cookie", cookie);
      }
      for (const cookie of refreshedSetCookies) {
        response.headers.append("Set-Cookie", cookie);
      }
      if (isNewCsrfToken && csrfToken) {
        const parts = [
          `${CSRF_COOKIE_NAME}=${csrfToken}`,
          `Path=/`,
          `SameSite=Strict`,
          `Max-Age=${60 * 60 * 24}`,
        ];
        parts.push(`Secure`);
        response.headers.append("Set-Cookie", parts.join("; "));
      }

      // Defensive: discourage intermediate buffering by reverse proxies.
      response.headers.set("Cache-Control", "no-cache, no-transform");
      response.headers.set("X-Accel-Buffering", "no");

      return response;
    }

    const responseBody = await backendRes.arrayBuffer();

    // 204/205/304 statuses cannot carry a body in the Next.js Response constructor
    const isNoContent = [204, 205, 304].includes(backendRes.status);

    const response = new NextResponse(isNoContent ? null : responseBody, {
      status: backendRes.status,
      statusText: backendRes.statusText,
    });

    backendRes.headers.forEach((value, key) => {
      if (!SKIP_RESPONSE_HEADERS.has(key.toLowerCase())) {
        response.headers.set(key, value);
      }
    });

    // Forward Set-Cookie headers as-is — cookie names match between backend and browser
    for (const cookie of backendRes.headers.getSetCookie()) {
      response.headers.append("Set-Cookie", cookie);
    }

    // Forward cookies from the silent refresh so the browser stores the fresh tokens.
    // Appended after backendRes cookies so they win for conflicting names (newer wins).
    for (const cookie of refreshedSetCookies) {
      response.headers.append("Set-Cookie", cookie);
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
      parts.push(`Secure`);
      response.headers.append("Set-Cookie", parts.join("; "));
    }

    if (pathname === LOGOUT_PATH) {
      clearAuthCookies(response);
    }

    return response;
  } catch (error) {
    // Logout: retry backend call to invalidate the refresh token server-side
    if (pathname === LOGOUT_PATH) {
      for (let attempt = 1; attempt <= MAX_LOGOUT_RETRIES; attempt++) {
        try {
          await fetch(url.toString(), {
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
