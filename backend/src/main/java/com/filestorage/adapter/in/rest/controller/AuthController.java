package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.request.ForgotPasswordRequest;
import com.filestorage.adapter.in.rest.dto.request.ResetPasswordRequest;
import com.filestorage.adapter.in.rest.dto.request.SignInRequest;
import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.dto.response.UserResponse;
import com.filestorage.application.usecase.user.AuthService;
import com.filestorage.application.usecase.user.CookieService;
import com.filestorage.application.usecase.user.EmailVerificationService;
import com.filestorage.application.usecase.user.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<String>> signIn(@RequestBody @Valid SignInRequest payload) {
        var token = authService.processSignIn(payload);

        var accessTokenCookie  = cookieService.createAccessTokenCookie(token.accessToken());
        var refreshTokenCookie = cookieService.createRefreshTokenCookie(token.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(ApiResponse.buildSuccess("Login success"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(
            @CookieValue(name = "refresh-token", required = false) String refreshToken) {
        var token = authService.processRefresh(refreshToken);

        var accessTokenCookie  = cookieService.createAccessTokenCookie(token.accessToken());
        var refreshTokenCookie = cookieService.createRefreshTokenCookie(token.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(ApiResponse.buildMessage("Refresh success"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @CookieValue(name = "refresh-token", required = false) String refreshToken,
            @CookieValue(name = "access-token", required = false) String accessToken) {
        authService.logout(refreshToken, accessToken);

        var cleanAccess  = cookieService.cleanAccessTokenCookie();
        var cleanRefresh = cookieService.cleanRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanAccess.toString())
                .header(HttpHeaders.SET_COOKIE, cleanRefresh.toString())
                .body(ApiResponse.buildMessage("Logout success"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        var user = authService.getUserInfo();
        return ResponseEntity.ok(ApiResponse.buildSuccess(user));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @RequestBody @Valid ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request.email());
        // Always return success to prevent email enumeration
        return ResponseEntity.ok(ApiResponse.buildMessage(
                "If the email exists, a password reset link has been sent."));
    }

    @PostMapping("/verify-reset-token")
    public ResponseEntity<ApiResponse<String>> verifyResetToken(
            @RequestParam String token) {
        passwordResetService.validateResetToken(token);
        return ResponseEntity.ok(ApiResponse.buildMessage("Reset token is valid."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @RequestBody @Valid ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request.token(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.ok(ApiResponse.buildMessage(
                "Password has been reset successfully. Please log in."));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.buildMessage("Email verified successfully."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<String>> resendVerification(
            @RequestBody @Valid ForgotPasswordRequest request) {
        emailVerificationService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.buildMessage(
                "If the email exists and is not yet verified, a new verification link has been sent."));
    }
}
