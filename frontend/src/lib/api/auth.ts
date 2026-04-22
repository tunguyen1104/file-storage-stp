import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import { AppError, toAppError } from "@/lib/api/core/api-error";
import type { ApiResponse } from "@/types/api.types";
import type { AuthUser } from "@/contexts/auth-context";
import type { SignInRequest, ResetPasswordRequest } from "@/types/auth/auth";

export async function signIn(
  email: string,
  password: string
): Promise<ApiResponse<string>> {
  const response = await apiClient.post<ApiResponse<string>>(
    API_ROUTES.AUTH.LOGIN,
    { email, password } satisfies SignInRequest
  );
  return response.data;
}

export async function refreshToken(): Promise<ApiResponse> {
  const response = await apiClient.post<ApiResponse>(
    API_ROUTES.AUTH.REFRESH
  );
  return response.data;
}

export async function logOut(): Promise<ApiResponse> {
  const response = await apiClient.post<ApiResponse>(
    API_ROUTES.AUTH.LOGOUT
  );
  return response.data;
}

export async function getMe(): Promise<AuthUser> {
  try {
    const response = await apiClient.get<ApiResponse<AuthUser>>(
      API_ROUTES.AUTH.ME
    );
    if (!response.data.success || !response.data.data) {
      throw new AppError(response.data.message ?? "Failed to fetch user profile");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function forgotPassword(
  email: string
): Promise<ApiResponse> {
  const response = await apiClient.post<ApiResponse>(
    API_ROUTES.AUTH.FORGOT_PASSWORD,
    { email }
  );
  return response.data;
}

export async function verifyResetToken(
  token: string
): Promise<ApiResponse> {
  const response = await apiClient.post<ApiResponse>(
    API_ROUTES.AUTH.VERIFY_RESET_TOKEN,
    null,
    { params: { token } }
  );
  return response.data;
}

export async function resetPassword(
  token: string,
  newPassword: string,
  confirmPassword: string
): Promise<ApiResponse> {
  const response = await apiClient.post<ApiResponse>(
    API_ROUTES.AUTH.RESET_PASSWORD,
    { token, newPassword, confirmPassword } satisfies ResetPasswordRequest
  );
  return response.data;
}

export async function verifyEmail(token: string): Promise<ApiResponse> {
  const response = await apiClient.get<ApiResponse>(
    API_ROUTES.AUTH.VERIFY_EMAIL,
    { params: { token } }
  );
  return response.data;
}

export async function resendVerification(
  email: string
): Promise<ApiResponse> {
  const response = await apiClient.post<ApiResponse>(
    API_ROUTES.AUTH.RESEND_VERIFICATION,
    { email }
  );
  return response.data;
}
