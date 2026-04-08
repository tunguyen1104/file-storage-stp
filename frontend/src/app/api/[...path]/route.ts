import { type NextRequest } from "next/server";
import { proxyToBackend } from "../_lib/proxy";

/**
 * Single catch-all BFF Route Handler.
 *
 * Proxies every `/api/*` request to the real backend, extracting
 * httpOnly cookies server-side and attaching them as Authorization
 * headers. No token ever touches client-side JavaScript.
 */

export async function GET(req: NextRequest) {
  return proxyToBackend(req);
}

export async function POST(req: NextRequest) {
  return proxyToBackend(req);
}

export async function PUT(req: NextRequest) {
  return proxyToBackend(req);
}

export async function PATCH(req: NextRequest) {
  return proxyToBackend(req);
}

export async function DELETE(req: NextRequest) {
  return proxyToBackend(req);
}
