import { NextResponse } from "next/server";
import {
  generateCsrfToken,
  CSRF_COOKIE_NAME,
} from "@/lib/csrf";

/**
 * Token Session Storage
 *
 * This endpoint implements a POST-redirect-GET pattern to remove sensitive
 * tokens from URLs. When a user clicks an email link with a token:
 *
 * 1. Page reads token from URL query param
 * 2. Page POSTs token to this endpoint
 * 3. This endpoint stores token in a short-lived httpOnly cookie
 * 4. Page clears the URL (history.replaceState) and reads token from cookie
 *
 * This prevents token exposure in:
 * - Browser history
 * - Referer headers
 * - Server/proxy logs
 * - Screenshots
 */

const TOKEN_COOKIE_NAME = "pending-token";
const TOKEN_COOKIE_MAX_AGE = 300; // 5 minutes

export async function POST(request: Request) {
  try {
    const { token, type } = await request.json();

    if (!token || typeof token !== "string") {
      return NextResponse.json(
        { status: "ERROR", message: "Token required" },
        { status: 400 }
      );
    }

    if (!type || !["verify", "reset"].includes(type)) {
      return NextResponse.json(
        { status: "ERROR", message: "Invalid token type" },
        { status: 400 }
      );
    }

    // Store token in httpOnly cookie with short expiry
    const response = NextResponse.json({ status: "SUCCESS" });

    response.cookies.set(TOKEN_COOKIE_NAME, JSON.stringify({ token, type }), {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "strict",
      path: "/",
      maxAge: TOKEN_COOKIE_MAX_AGE,
    });

    // Ensure CSRF token exists
    const csrfToken = generateCsrfToken();
    response.cookies.set(CSRF_COOKIE_NAME, csrfToken, {
      httpOnly: false,
      secure: process.env.NODE_ENV === "production",
      sameSite: "strict",
      path: "/",
      maxAge: 60 * 60 * 24,
    });

    return response;
  } catch {
    return NextResponse.json(
      { status: "ERROR", message: "Invalid request" },
      { status: 400 }
    );
  }
}

export async function GET(request: Request) {
  const cookieHeader = request.headers.get("cookie") || "";
  const cookies = Object.fromEntries(
    cookieHeader.split(";").map((c) => {
      const [key, ...val] = c.trim().split("=");
      return [key, val.join("=")];
    })
  );

  const tokenData = cookies[TOKEN_COOKIE_NAME];
  if (!tokenData) {
    return NextResponse.json(
      { status: "ERROR", message: "No pending token" },
      { status: 404 }
    );
  }

  try {
    const { token, type } = JSON.parse(decodeURIComponent(tokenData));

    // Clear the token cookie after reading (one-time use)
    const response = NextResponse.json({ status: "SUCCESS", token, type });
    response.cookies.set(TOKEN_COOKIE_NAME, "", {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "strict",
      path: "/",
      maxAge: 0,
    });

    return response;
  } catch {
    return NextResponse.json(
      { status: "ERROR", message: "Invalid token data" },
      { status: 400 }
    );
  }
}
