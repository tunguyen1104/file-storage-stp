import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import {
  generateCsrfToken,
  validateCsrfToken,
  CSRF_COOKIE_NAME,
  CSRF_HEADER_NAME,
} from "@/lib/csrf";

/**
 * BFF (Backend-For-Frontend) proxy for every `/api/v1/*` route the Next.js app exposes.
 *
 * Why it exists:
 *   - Keeps auth tokens (access + refresh) in httpOnly cookies so JS can't read them.
 *   - Adds CSRF protection on mutating requests via the double-submit-cookie pattern.
 *   - Performs **silent token refresh** when the access token expired but the refresh
 *     token is still good — so users don't get bounced to /login mid-session.
 *   - Forwards the upstream response body verbatim, including SSE streams.
 *
 * Reading order (top → bottom of `proxyToBackend`):
 *   1. SSRF allowlist        — only forward to paths the FE owns.
 *   2. CSRF validation       — mutating methods only.
 *   3. Read auth cookies + run silent refresh if the access token is gone.
 *   4. Build & send the upstream request to the real backend.
 *   5. Pipe the response back, choosing streaming vs. buffered based on content-type.
 *   6. Special-case `/auth/logout`: always clear auth cookies, retry on transport error.
 */

// ─── Configuration ────────────────────────────────────────────────────────────

const BACKEND_URL =
  process.env.BACKEND_URL ??
  process.env.NEXT_PUBLIC_BACKEND_URL ??
  "http://localhost:8081";

// SSRF guard: every incoming path must start with one of these.
// New backend resource groups need an entry here before they're reachable.
const ALLOWED_PREFIXES = [
  "/api/v1/auth/",
  "/api/v1/folders",
  "/api/v1/files",
  "/api/v1/uploads",
  "/api/v1/users",
  "/api/v1/tenants",
  "/api/v1/audit-logs",
  "/api/v1/search",
  "/api/v1/shares",
];

// HTTP methods that change server state and therefore require CSRF protection.
const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

// Cookies cleared whenever the user logs out (or the backend signals a session kill).
const AUTH_COOKIE_NAMES = ["access-token", "refresh-token"] as const;

// Hop-by-hop / transport-layer headers we must not blindly forward from the backend.
// Set-Cookie is handled separately via getSetCookie() so multiple cookies survive.
const SKIP_RESPONSE_HEADERS = new Set([
  "transfer-encoding",
  "connection",
  "keep-alive",
  "set-cookie",
]);

// Anonymous endpoints (no session yet) — there's nothing for CSRF to protect, so skip the check.
// Keep this list tight: ANY authenticated endpoint must NOT appear here.
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

// ─── Small response helpers ───────────────────────────────────────────────────

/** Clear the access + refresh cookies on the outgoing response. */
function clearAuthCookies(response: NextResponse): void {
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

/** 403 with a clear reason string in the JSON body. */
function forbidden(reason: string): NextResponse {
  return NextResponse.json({ success: false, errors: reason }, { status: 403 });
}

// ─── Gatekeepers (run before any upstream work) ───────────────────────────────

/**
 * Reject requests whose pathname is outside the SSRF allowlist.
 * Returns null when the path is allowed.
 */
function rejectIfSsrf(pathname: string): NextResponse | null {
  const allowed = ALLOWED_PREFIXES.some((prefix) => pathname.startsWith(prefix));
  return allowed ? null : forbidden("Forbidden");
}

/**
 * Double-submit cookie check: the JS-readable `csrf-token` cookie must match the
 * `X-CSRF-Token` request header. Skipped for anonymous endpoints (`CSRF_EXEMPT_PATHS`).
 *
 * Returns null when the request passes (or when the check doesn't apply).
 */
function rejectIfCsrfFails(
  req: NextRequest,
  cookieCsrfToken: string | undefined,
  pathname: string
): NextResponse | null {
  const isMutating = MUTATING_METHODS.has(req.method);
  if (!isMutating || CSRF_EXEMPT_PATHS.has(pathname)) {
    return null;
  }

  const headerCsrfToken = req.headers.get(CSRF_HEADER_NAME);
  if (validateCsrfToken(cookieCsrfToken, headerCsrfToken)) {
    return null;
  }
  return forbidden("Invalid CSRF token");
}

// ─── CSRF token bootstrap ─────────────────────────────────────────────────────

/**
 * Return the current CSRF token, or mint a fresh one if none exists yet.
 *
 * `isFreshlyMinted = true` tells the caller it must Set-Cookie the new value on
 * the outgoing response so the browser stores it for the next round-trip.
 */
function readOrMintCsrfToken(existing: string | undefined): {
  token: string;
  isFreshlyMinted: boolean;
} {
  if (existing) return { token: existing, isFreshlyMinted: false };
  return { token: generateCsrfToken(), isFreshlyMinted: true };
}

/** Build the Set-Cookie value for the first-issue CSRF cookie. */
function buildFreshCsrfCookieHeader(token: string): string {
  const parts = [
    `${CSRF_COOKIE_NAME}=${token}`,
    `Path=/`,
    `SameSite=Strict`,
    `Max-Age=${60 * 60 * 24}`,
  ];
  if (process.env.NODE_ENV === "production") parts.push(`Secure`);
  return parts.join("; ");
}

// ─── Silent token refresh ─────────────────────────────────────────────────────

type SilentRefreshResult = {
  accessToken: string;
  refreshToken: string;     // possibly rotated by the backend
  setCookies: string[];     // raw Set-Cookie strings to pipe back to the browser
};

/**
 * Determine whether we should attempt a silent refresh.
 *
 * Triggers when:
 *   - the request targets a protected endpoint (i.e. NOT anonymous, NOT /refresh itself), AND
 *   - the browser has no access-token cookie, BUT
 *   - it still has a refresh-token cookie we can spend.
 */
function shouldAttemptSilentRefresh(
  pathname: string,
  accessToken: string | undefined,
  refreshToken: string | undefined
): boolean {
  if (accessToken) return false;
  if (!refreshToken) return false;
  if (CSRF_EXEMPT_PATHS.has(pathname)) return false;
  if (pathname === REFRESH_PATH) return false;
  return true;
}

/**
 * Call the backend's /auth/refresh directly to mint a fresh access token.
 *
 * On success, returns the new access-token value plus the full Set-Cookie strings
 * the backend issued (so we can forward them to the browser with attributes intact).
 *
 * On any failure (network, 4xx, missing access cookie) returns null — the caller
 * should fall through and let the upstream backend reject the unauthenticated request.
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
    const accessTokenCookie = setCookies.find((c) => c.startsWith("access-token="));
    if (!accessTokenCookie) return null;

    const accessToken = accessTokenCookie.split(";")[0].split("=")[1];
    return accessToken ? { accessToken, setCookies } : null;
  } catch {
    return null;
  }
}

/**
 * If the gating predicate matches, run the silent refresh and return updated tokens
 * + Set-Cookie strings the caller must forward. Returns null when no refresh ran
 * (either the predicate failed or the backend rejected the refresh).
 */
async function trySilentRefresh(
  pathname: string,
  accessToken: string | undefined,
  refreshToken: string | undefined,
  csrfToken: string | undefined
): Promise<SilentRefreshResult | null> {
  if (!shouldAttemptSilentRefresh(pathname, accessToken, refreshToken)) {
    return null;
  }

  const refreshed = await refreshTokensViaBackend(refreshToken!, csrfToken);
  if (!refreshed) return null;

  // If the backend rotated the refresh token, use the rotated value for this
  // request — otherwise carry forward the original.
  const rotatedRefreshCookie = refreshed.setCookies.find((c) =>
    c.startsWith("refresh-token=")
  );
  const nextRefreshToken = rotatedRefreshCookie
    ? rotatedRefreshCookie.split(";")[0].split("=")[1]
    : refreshToken!;

  return {
    accessToken: refreshed.accessToken,
    refreshToken: nextRefreshToken,
    setCookies: refreshed.setCookies,
  };
}

// ─── Upstream request construction ────────────────────────────────────────────

/** Build the Cookie + CSRF + Content-Type headers we send to the backend. */
function buildUpstreamHeaders(
  req: NextRequest,
  tokens: {
    accessToken: string | undefined;
    refreshToken: string | undefined;
    csrfToken: string | undefined;
  }
): Headers {
  const headers = new Headers();
  const isMutating = MUTATING_METHODS.has(req.method);

  if (isMutating) {
    headers.set(
      "Content-Type",
      req.headers.get("content-type") ?? "application/json"
    );
  }

  const accept = req.headers.get("accept");
  if (accept) headers.set("Accept", accept);

  // Forward the browser's User-Agent so audit-log rows record the real client,
  // not "node" (the default UA of the undici fetch used by this route handler).
  const userAgent = req.headers.get("user-agent");
  if (userAgent) headers.set("User-Agent", userAgent);

  // Forward the real client IP so backend (RequestContext.getClientIp) doesn't
  // log 127.0.0.1 for every request that came through this Next.js BFF.
  // Preserve any existing X-Forwarded-For chain set by an upstream proxy.
  const incomingXff = req.headers.get("x-forwarded-for");
  const clientIp =
    incomingXff?.split(",")[0]?.trim() ||
    req.headers.get("x-real-ip") ||
    null;
  if (incomingXff) {
    headers.set("X-Forwarded-For", incomingXff);
  } else if (clientIp) {
    headers.set("X-Forwarded-For", clientIp);
  }

  // Forward cookies to the backend using hyphenated names (backend's contract).
  const cookieParts: string[] = [];
  if (tokens.accessToken) cookieParts.push(`access-token=${tokens.accessToken}`);
  if (tokens.refreshToken) cookieParts.push(`refresh-token=${tokens.refreshToken}`);
  if (tokens.csrfToken) cookieParts.push(`${CSRF_COOKIE_NAME}=${tokens.csrfToken}`);
  if (cookieParts.length > 0) {
    headers.set("Cookie", cookieParts.join("; "));
  }

  // The CSRF header travels alongside the cookie on mutating requests so the
  // backend can also enforce the double-submit check.
  if (isMutating && tokens.csrfToken) {
    headers.set(CSRF_HEADER_NAME, tokens.csrfToken);
  }

  return headers;
}

// ─── Response forwarding ──────────────────────────────────────────────────────

/** Copy non-hop-by-hop headers from the backend response onto our outgoing response. */
function forwardResponseHeaders(target: NextResponse, source: Response): void {
  source.headers.forEach((value, key) => {
    if (!SKIP_RESPONSE_HEADERS.has(key.toLowerCase())) {
      target.headers.set(key, value);
    }
  });
}

/**
 * Pipe Set-Cookie headers from every source onto the response, in priority order:
 *   1. Cookies the backend set on this request's response.
 *   2. Cookies the silent-refresh issued (appended AFTER, so they win on conflicts).
 *   3. The first-issue CSRF cookie if we just minted one.
 *
 * Using `headers.append("Set-Cookie", …)` rather than `response.cookies.set()` is
 * deliberate: the ResponseCookies API does a delete-then-rewrite of ALL Set-Cookie
 * headers, which would drop the auth cookies appended in step 1/2.
 */
function pipeSetCookies(
  response: NextResponse,
  backendRes: Response,
  refreshedSetCookies: string[],
  freshCsrfCookie: string | null
): void {
  for (const cookie of backendRes.headers.getSetCookie()) {
    response.headers.append("Set-Cookie", cookie);
  }
  for (const cookie of refreshedSetCookies) {
    response.headers.append("Set-Cookie", cookie);
  }
  if (freshCsrfCookie) {
    response.headers.append("Set-Cookie", freshCsrfCookie);
  }
}

/**
 * Build the response for an SSE upstream (text/event-stream).
 *
 * Critical: we pipe `backendRes.body` (a ReadableStream) straight through —
 * NEVER call `.arrayBuffer()` or the SSE events get buffered and the client sees
 * nothing until the backend closes the connection.
 */
function buildStreamingResponse(
  backendRes: Response,
  refreshedSetCookies: string[],
  freshCsrfCookie: string | null
): NextResponse {
  const response = new NextResponse(backendRes.body, {
    status: backendRes.status,
    statusText: backendRes.statusText,
  });

  forwardResponseHeaders(response, backendRes);
  pipeSetCookies(response, backendRes, refreshedSetCookies, freshCsrfCookie);

  // Hint to reverse proxies (nginx, etc.) not to buffer the stream.
  response.headers.set("Cache-Control", "no-cache, no-transform");
  response.headers.set("X-Accel-Buffering", "no");

  return response;
}

/** Build the response for a JSON/binary upstream that we can safely buffer. */
async function buildBufferedResponse(
  backendRes: Response,
  refreshedSetCookies: string[],
  freshCsrfCookie: string | null
): Promise<NextResponse> {
  const body = await backendRes.arrayBuffer();

  // 204/205/304 cannot legally carry a body in the Response constructor.
  const isNoContentStatus = [204, 205, 304].includes(backendRes.status);

  const response = new NextResponse(isNoContentStatus ? null : body, {
    status: backendRes.status,
    statusText: backendRes.statusText,
  });

  forwardResponseHeaders(response, backendRes);
  pipeSetCookies(response, backendRes, refreshedSetCookies, freshCsrfCookie);

  return response;
}

// ─── Logout failure recovery ──────────────────────────────────────────────────

/**
 * Best-effort retry of the backend logout when the original fetch threw.
 *
 * The refresh token must be invalidated server-side to be revocation-safe,
 * so a transient transport error shouldn't leave the session valid on the
 * backend. We swallow per-attempt errors and log; the response to the browser
 * always clears the auth cookies regardless.
 */
async function retryLogoutBackend(url: string, headers: Headers): Promise<void> {
  for (let attempt = 1; attempt <= MAX_LOGOUT_RETRIES; attempt++) {
    try {
      await fetch(url, { method: "POST", headers, redirect: "manual" });
      return;
    } catch {
      console.warn(
        `[BFF Proxy] Logout retry ${attempt}/${MAX_LOGOUT_RETRIES} failed`
      );
    }
  }
}

// ─── Main entry point ─────────────────────────────────────────────────────────

/**
 * Forward an incoming Next.js request to the real backend, handling auth + CSRF.
 * See file-level comment for the full request lifecycle.
 */
export async function proxyToBackend(req: NextRequest): Promise<NextResponse> {
  const pathname = req.nextUrl.pathname;
  const search = req.nextUrl.search;

  // (1) SSRF allowlist
  const ssrfBlock = rejectIfSsrf(pathname);
  if (ssrfBlock) return ssrfBlock;

  const cookieStore = await cookies();

  // (2) CSRF validation
  const csrfBlock = rejectIfCsrfFails(
    req,
    cookieStore.get(CSRF_COOKIE_NAME)?.value,
    pathname
  );
  if (csrfBlock) return csrfBlock;

  // (3) Auth state — read cookies, mint a CSRF token if first contact, attempt silent refresh
  let accessToken = cookieStore.get("access-token")?.value;
  let refreshToken = cookieStore.get("refresh-token")?.value;

  const { token: csrfToken, isFreshlyMinted: isNewCsrf } = readOrMintCsrfToken(
    cookieStore.get(CSRF_COOKIE_NAME)?.value
  );
  const freshCsrfCookie = isNewCsrf ? buildFreshCsrfCookieHeader(csrfToken) : null;

  const refreshed = await trySilentRefresh(pathname, accessToken, refreshToken, csrfToken);
  if (refreshed) {
    accessToken = refreshed.accessToken;
    refreshToken = refreshed.refreshToken;
  }
  const refreshedSetCookies = refreshed?.setCookies ?? [];

  // (4) Build the upstream request
  const upstreamUrl = new URL(pathname, BACKEND_URL);
  upstreamUrl.search = search;

  const upstreamHeaders = buildUpstreamHeaders(req, {
    accessToken,
    refreshToken,
    csrfToken,
  });

  const init: RequestInit = {
    method: req.method,
    headers: upstreamHeaders,
    redirect: "manual",
  };
  if (MUTATING_METHODS.has(req.method)) {
    const body = await req.arrayBuffer();
    if (body.byteLength > 0) init.body = body;
  }

  // (5) Forward and pipe back the response
  try {
    const backendRes = await fetch(upstreamUrl.toString(), init);
    const contentType = backendRes.headers.get("content-type") ?? "";
    const isSse = contentType.startsWith("text/event-stream");

    if (isSse) {
      return buildStreamingResponse(backendRes, refreshedSetCookies, freshCsrfCookie);
    }

    const response = await buildBufferedResponse(
      backendRes,
      refreshedSetCookies,
      freshCsrfCookie
    );

    // (6) Special case: logout always strips auth cookies on its way out.
    if (pathname === LOGOUT_PATH) {
      clearAuthCookies(response);
    }
    return response;
  } catch (error) {
    if (pathname === LOGOUT_PATH) {
      // Even on transport failure, do our best to invalidate the refresh token
      // server-side, then return success to the browser with auth cookies cleared.
      await retryLogoutBackend(upstreamUrl.toString(), upstreamHeaders);
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
