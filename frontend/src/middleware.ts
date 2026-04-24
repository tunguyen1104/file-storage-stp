import { type NextRequest, NextResponse } from "next/server";
import { jwtVerify } from "jose";
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
    "/search",
    "/audit-logs",
    "/users",
    "/unauthorized",
  ],
  MEMBER: ["/files", "uploads", "/search", "/unauthorized"],
};

/**
 * Redirect to /login and clear both auth cookies.
 */
function redirectToLogin(request: NextRequest): NextResponse {
  const response = NextResponse.redirect(new URL("/login", request.url));
  response.cookies.delete("accessToken");
  response.cookies.delete("refreshToken"); // #14: also clear refresh token
  return response;
}

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Allow root path to redirect
  if (pathname === "/") {
    const accessToken = request.cookies.get("accessToken")?.value;

    if (accessToken) {
      return NextResponse.redirect(new URL("/files", request.url));
    }
    return NextResponse.redirect(new URL("/login", request.url));
  }

  // 1. Get access token from cookies
  const accessToken = request.cookies.get("accessToken")?.value;

  // 2. Unauthenticated user
  if (!accessToken) {
    if (PUBLIC_ROUTES.some((route) => pathname.startsWith(route))) {
      return NextResponse.next();
    }
    return NextResponse.redirect(new URL("/login", request.url));
  }

  // 3. Verify token signature + expiry
  let decoded: DecodedToken;
  try {
    if (!JWT_SECRET) {
      // Development only: allow requests without JWT verification
      // This block is unreachable in production due to the fail-fast check above
      console.warn(
        "[Middleware] JWT_SECRET not set — auth check skipped (dev mode only)"
      );
      return NextResponse.next();
    }

    const secret = Buffer.from(JWT_SECRET, "base64");
    const { payload } = await jwtVerify(accessToken, secret);
    decoded = payload as unknown as DecodedToken;
  } catch {
    return redirectToLogin(request);
  }

  // 4. Authenticated user on auth pages → redirect to /files
  if (PUBLIC_ROUTES.some((route) => pathname.startsWith(route))) {
    return NextResponse.redirect(new URL("/files", request.url));
  }

  // 5. Role-based access control
  const allowedRoutes = ROLE_ROUTE_MAP[decoded.role] ?? [];
  const isAllowed = allowedRoutes.some((route) => pathname.startsWith(route));
  if (!isAllowed) {
    console.log(111, decoded.role, pathname);
    return NextResponse.redirect(new URL("/unauthorized", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/((?!_next|api|trpc|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)",
  ],
};