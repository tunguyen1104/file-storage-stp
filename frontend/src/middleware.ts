import { type NextRequest, NextResponse } from "next/server";
import { jwtVerify, errors as joseErrors } from "jose";
import { type DecodedToken } from "@/lib/auth";

/**
 * SECURITY NOTE:
 * - JWT_SECRET MUST be set in production — middleware will reject all requests otherwise.
 * - In development (NODE_ENV !== 'production'), a warning is logged but requests proceed.
 * - Backend MUST independently enforce authorization on every request regardless.
 */

const PUBLIC_ROUTES = [
  "/login",
  "/forgot-password",
  "/verify",
  "/check-email",
  "/reset-password",
];

const JWT_SECRET = process.env.JWT_SECRET;
const IS_PRODUCTION = process.env.NODE_ENV === "production";

// Fail-fast in production if JWT_SECRET is not configured
if (IS_PRODUCTION && !JWT_SECRET) {
  throw new Error(
    "CRITICAL: JWT_SECRET environment variable is required in production"
  );
}

const ROLE_ROUTE_MAP: Record<string, string[]> = {
  SUPER_ADMIN: [
    "/files",
    "/search",
    "/audit-logs",
    "/tenants",
    "/users",
    "/unauthorized",
  ],
  TENANT_ADMIN: [
    "/files",
    "/shared",
    "/search",
    "/audit-logs",
    "/users",
    "/unauthorized",
  ],
  MEMBER: ["/files", "/shared", "/upload", "/search", "/unauthorized"],
};

const BACKEND_URL_FOR_REFRESH =
  process.env.BACKEND_URL ??
  process.env.NEXT_PUBLIC_BACKEND_URL ??
  "http://localhost:8081";

function redirectToLogin(request: NextRequest): NextResponse {
  const response = NextResponse.redirect(new URL("/login", request.url));
  response.cookies.delete("access-token");
  response.cookies.delete("refresh-token");
  return response;
}

/**
 * Call the backend refresh endpoint directly and return the new access token
 * value + full Set-Cookie headers so attributes (expiry, path, etc.) are preserved.
 */
async function refreshAccessToken(
  refreshToken: string
): Promise<{ value: string; setCookies: string[] } | null> {
  try {
    const res = await fetch(`${BACKEND_URL_FOR_REFRESH}/api/v1/auth/refresh`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Cookie: `refresh-token=${refreshToken}`,
      },
    });
    if (!res.ok) return null;

    const setCookies = res.headers.getSetCookie();
    const accessTokenCookie = setCookies.find((c) =>
      c.startsWith("access-token=")
    );
    if (!accessTokenCookie) return null;

    const value = accessTokenCookie.split(";")[0].split("=")[1];
    return value ? { value, setCookies } : null;
  } catch {
    return null;
  }
}

/**
 * Forward the refreshed token to:
 * - the current request (modified Cookie header, so SSR pages see the new token)
 * - the browser (Set-Cookie response header, so subsequent requests use it)
 */
function continueWithNewToken(
  request: NextRequest,
  newAccessToken: string,
  setCookies: string[]
): NextResponse {
  const requestHeaders = new Headers(request.headers);
  const updatedCookie = (requestHeaders.get("cookie") ?? "")
    .split(";")
    .map((c) => c.trim())
    .filter((c) => !c.startsWith("access-token="))
    .concat(`access-token=${newAccessToken}`)
    .join("; ");
  requestHeaders.set("cookie", updatedCookie);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  for (const cookie of setCookies) {
    response.headers.append("Set-Cookie", cookie);
  }
  return response;
}

/**
 * Attempt a silent token refresh for a protected route.
 * On success: verify the new token, run RBAC, and continue.
 * On any failure: clear cookies and redirect to login.
 */
async function refreshOrLogin(request: NextRequest): Promise<NextResponse> {
  const refreshToken = request.cookies.get("refresh-token")?.value;
  if (!refreshToken) return redirectToLogin(request);

  const refreshResult = await refreshAccessToken(refreshToken);
  if (!refreshResult) return redirectToLogin(request);

  const { value: newAccessToken, setCookies } = refreshResult;

  try {
    const secret = Buffer.from(JWT_SECRET!, "base64");
    const { payload } = await jwtVerify(newAccessToken, secret);
    const decoded = payload as unknown as DecodedToken;
    const { pathname } = request.nextUrl;

    const isAllowed = (ROLE_ROUTE_MAP[decoded.role] ?? []).some((r) =>
      pathname.startsWith(r)
    );
    if (!isAllowed) {
      return NextResponse.redirect(new URL("/unauthorized", request.url));
    }

    return continueWithNewToken(request, newAccessToken, setCookies);
  } catch {
    return redirectToLogin(request);
  }
}

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const accessToken = request.cookies.get("access-token")?.value;

  if (pathname === "/") {
    return NextResponse.redirect(
      new URL(accessToken ? "/files" : "/login", request.url)
    );
  }

  const isPublicRoute = PUBLIC_ROUTES.some((route) =>
    pathname.startsWith(route)
  );

  // Development only: skip JWT verification when secret is absent
  // This block is unreachable in production due to the fail-fast check above
  if (!JWT_SECRET) {
    console.warn(
      "[Middleware] JWT_SECRET not set — auth check skipped (dev mode only)"
    );
    return NextResponse.next();
  }

  // No access token: allow public routes; for protected routes attempt silent refresh
  if (!accessToken) {
    if (isPublicRoute) return NextResponse.next();
    return refreshOrLogin(request);
  }

  // Verify token signature + expiry
  try {
    const secret = Buffer.from(JWT_SECRET, "base64");
    const { payload } = await jwtVerify(accessToken, secret);
    const decoded = payload as unknown as DecodedToken;

    // Authenticated user on a public route → send to /files
    if (isPublicRoute) {
      return NextResponse.redirect(new URL("/files", request.url));
    }

    // Role-based access control
    const isAllowed = (ROLE_ROUTE_MAP[decoded.role] ?? []).some((r) =>
      pathname.startsWith(r)
    );
    return isAllowed
      ? NextResponse.next()
      : NextResponse.redirect(new URL("/unauthorized", request.url));
  } catch (err) {
    // Expired token: silently refresh for protected routes, let public routes through
    if (err instanceof joseErrors.JWTExpired) {
      if (isPublicRoute) return NextResponse.next();
      return refreshOrLogin(request);
    }
    // Invalid/tampered token: clear session immediately
    return redirectToLogin(request);
  }
}

export const config = {
  matcher: [
    "/((?!_next|api|trpc|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)",
  ],
};
