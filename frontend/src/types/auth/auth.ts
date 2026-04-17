export type SignInRequest = {
  email: string;
  password: string;
}

export type ResetPasswordRequest = {
  token: string;
  newPassword: string;
  confirmPassword: string;
}
