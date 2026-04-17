import axios, {
  type AxiosInstance,
  type AxiosError,
  type InternalAxiosRequestConfig,
} from "axios";
import { getCsrfToken, CSRF_HEADER_NAME } from "./csrf";
import type { ApiResponse } from "@/types/api.types";

// ─── Error code constants ──────────────────────────────────────
const ERROR_CODE_TOKEN_EXPIRED = 1002;

// ─── Promise-based Refresh Lock ─────────────────────────────────
let refreshPromise: Promise<void> | null = null;

// ─── Axios Instance ──────────────────────────────────────────
export const apiClient: AxiosInstance = axios.create({
  headers: { "Content-Type": "application/json" },
  withCredentials: true, // httpOnly cookies sent to BFF automatically
});

// ─── Request Interceptor ────────────────────────────────────
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  // Attach CSRF token to state-changing requests
  const method = config.method?.toUpperCase();
  if (method && ["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
    const csrfToken = getCsrfToken();
    if (csrfToken) {
      config.headers.set(CSRF_HEADER_NAME, csrfToken);
    }
  }

  return config;
});

// ─── Response Interceptor ───────────────────────────────────
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    // Never intercept the refresh request itself to avoid infinite loops
    if (originalRequest.url?.includes("/api/v1/auth/refresh")) {
      return Promise.reject(error);
    }

    // Check if this is a token-expired error (code=1002)
    const responseData = error.response?.data;
    const isTokenExpired =
      error.response?.status === 401 &&
      responseData?.code === ERROR_CODE_TOKEN_EXPIRED;

    // Only attempt refresh for token expiry, not other 401s
    if (!isTokenExpired || originalRequest._retry) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    // If a refresh is already in progress, wait for it instead of starting another
    if (refreshPromise) {
      try {
        await refreshPromise;
        return apiClient(originalRequest);
      } catch {
        return Promise.reject(error);
      }
    }

    // Start a new refresh and store the promise
    refreshPromise = (async () => {
      try {
        await axios.post("/api/v1/auth/refresh", {}, { withCredentials: true });
      } catch (refreshError) {
        // Force logout on any refresh failure
        if (
          typeof window !== "undefined" &&
          axios.isAxiosError(refreshError)
        ) {
          window.dispatchEvent(new Event("auth:logout"));
        }
        throw refreshError;
      }
    })();

    try {
      await refreshPromise;
      return apiClient(originalRequest);
    } catch (refreshError) {
      return Promise.reject(refreshError);
    } finally {
      refreshPromise = null;
    }
  }
);
