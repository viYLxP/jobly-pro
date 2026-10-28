package com.jobly.auth;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.jobly.user.Role;
import com.jobly.user.UserDto;

/**
 * Request and response shapes of the account endpoints.
 * أشكال الطلبات والردود لروابط الحسابات.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    // At least 8 characters with a letter and a digit. The 72-byte BCrypt limit is checked in PasswordRules.
    // 8 أحرف على الأقل مع حرف ورقم. وحد 72 بايت الخاص بـ BCrypt يُفحص في PasswordRules.
    public static final String PASSWORD_PATTERN = "^(?=.*\\p{L})(?=.*\\d).+$";

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = PASSWORD_PATTERN) String password,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Role role,
            // Required for employers (checked in the service). / مطلوب لصاحب العمل (يُفحص في الخدمة).
            @Size(max = 150) String companyName,
            @Pattern(regexp = "ar|en") String language) {
    }

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {
    }

    // Used by "resend verification" and "forgot password". / تُستخدم لـ"إعادة إرسال التفعيل" و"نسيت كلمة المرور".
    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record VerifyRequest(@NotBlank @Size(max = 100) String token) {
    }

    public record ResetRequest(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = PASSWORD_PATTERN) String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserDto user) {
    }

    // A short machine-readable result such as CHECK_EMAIL or VERIFIED. / نتيجة قصيرة مثل CHECK_EMAIL أو VERIFIED.
    public record StatusResponse(String status) {
    }
}
