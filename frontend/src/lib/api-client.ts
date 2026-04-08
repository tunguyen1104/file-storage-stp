import axios, {
  type AxiosInstance,
  type AxiosError,
  type InternalAxiosRequestConfig,
} from "axios";
import { getCsrfToken, CSRF_HEADER_NAME } from "./csrf";

// ─── Promise-based Refresh Lock ─────────────────────────────────
// Instead of a boolean flag + array, we use a single promise that
// all concurrent requests can await. This eliminates race conditions.
let refreshPromise: Promise<void> | null = null;

// ─── Axios Instance ──────────────────────────────────────────
export const apiClient: AxiosInstance = axios.create({
  headers: { "Content-Type": "application/json" },
  withCredentials: true, // httpOnly cookies sent to BFF automatically
});

// ─── Request Interceptor ────────────────────────────────────
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  // Token is sent via httpOnly cookie automatically (withCredentials: true).
  // BFF Route Handler extracts token and forwards to backend.

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
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    // Never intercept the refresh request itself to avoid infinite loops
    if (originalRequest.url?.includes("/api/auth/refresh")) {
      return Promise.reject(error);
    }

    // Only handle 401s that haven't been retried
    if (error.response?.status !== 401 || originalRequest._retry) {
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
        await axios.post("/api/auth/refresh", {}, { withCredentials: true });
      } catch (refreshError) {
        // Only force logout on 401 (invalid refresh token), not on 5xx
        if (
          typeof window !== "undefined" &&
          axios.isAxiosError(refreshError) &&
          refreshError.response?.status === 401
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
