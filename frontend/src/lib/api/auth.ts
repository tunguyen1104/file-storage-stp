import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import type { AuthUser } from "@/contexts/auth-context";

/**
 * NOTE: login() and logout() have been removed (#16).
 * These should ONLY be called via useAuth().signIn / useAuth().logOut
 * to ensure proper SWR cache mutation and routing.
 */

export async function getMe(): Promise<AuthUser> {
  const response = await apiClient.get(API_ROUTES.AUTH.ME);
  return response.data.data;
}

export async function verifyEmail(verifyCode: string) {
  // #5: Send token in body, not as query param (prevents log/Referer leaks)
  const response = await apiClient.post(API_ROUTES.AUTH.VERIFY_EMAIL, {
    verifyCode,
  });
  return response.data;
}

export async function forgotPassword(email: string) {
  const response = await apiClient.post(API_ROUTES.AUTH.FORGOT_PASSWORD, {
    email,
  });
  return response.data;
}

export async function resetPassword(token: string, newPassword: string) {
  const response = await apiClient.post(API_ROUTES.AUTH.RESET_PASSWORD, {
    token,
    newPassword,
  });
  return response.data;
}

export async function changePassword(
  currentPassword: string,
  newPassword: string
) {
  const response = await apiClient.post(API_ROUTES.AUTH.CHANGE_PASSWORD, {
    currentPassword,
    newPassword,
  });
  return response.data;
}
