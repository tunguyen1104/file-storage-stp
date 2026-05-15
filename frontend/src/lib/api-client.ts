import axios, {
  type AxiosInstance,
  type AxiosError,
  type InternalAxiosRequestConfig,
} from "axios";
import { API_ROUTES } from "@/lib/constants";
import { getCsrfToken, CSRF_HEADER_NAME } from "./csrf";
import { toAppError, type ApiErrorResponse } from "@/lib/api/core/api-error";

const AUTH_ERROR_CODES = {
  ACCESS_DENIED: 2,    // No token sent — try refresh first, logout only if refresh fails
  TOKEN_EXPIRED: 1002, // Token expired — refresh and retry
  INVALID_TOKEN: 1003, // Token tampered or revoked — logout immediately
} as const;

let refreshPromise: Promise<void> | null = null;

export const apiClient: AxiosInstance = axios.create({
  headers: { "Content-Type": "application/json" },
  withCredentials: true,
});

function logout(): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event("auth:logout"));
  }
}

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const method = config.method?.toUpperCase();
  if (method && ["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
    const csrfToken = getCsrfToken();
    if (csrfToken) {
      config.headers.set(CSRF_HEADER_NAME, csrfToken);
    }
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorResponse>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    // Guard: never intercept the refresh call itself to avoid infinite loops
    if (originalRequest.url?.includes(API_ROUTES.AUTH.REFRESH)) {
      return Promise.reject(toAppError(error));
    }

    const status = error.response?.status;
    const code = error.response?.data?.code;

    if (status !== 401) {
      return Promise.reject(toAppError(error));
    }

    switch (code) {
      case AUTH_ERROR_CODES.INVALID_TOKEN:
        // Token is tampered or already revoked — refresh token may also be invalid
        logout();
        return Promise.reject(toAppError(error));

      case AUTH_ERROR_CODES.ACCESS_DENIED:
      case AUTH_ERROR_CODES.TOKEN_EXPIRED:
        // No token sent OR token expired — try refresh; if it fails, logout
        break;

      default:
        return Promise.reject(toAppError(error));
    }

    // Don't retry a request that already went through a refresh cycle
    if (originalRequest._retry) {
      return Promise.reject(toAppError(error));
    }
    originalRequest._retry = true;

    // Deduplicate concurrent refreshes: wait for an in-flight refresh instead of starting a new one
    if (refreshPromise) {
      try {
        await refreshPromise;
        return apiClient(originalRequest);
      } catch {
        return Promise.reject(toAppError(error));
      }
    }

    refreshPromise = (async () => {
      try {
        await apiClient.post(API_ROUTES.AUTH.REFRESH, {});
      } catch (err: unknown) {
        const appErr = err as { status?: number };
        // Only logout for auth-specific failures (4xx). Network/server errors (5xx / no response)
        // don't mean the session is invalid, so we preserve the session.
        const isAuthFailure =
          typeof appErr?.status === "number" && appErr.status < 500;
        if (isAuthFailure) {
          logout();
        }
        throw err;
      }
    })();

    try {
      await refreshPromise;
      return apiClient(originalRequest);
    } catch (refreshError) {
      return Promise.reject(toAppError(refreshError));
    } finally {
      refreshPromise = null;
    }
  }
);
