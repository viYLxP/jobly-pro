package com.jobly.auth;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.jobly.common.ApiException;
import com.jobly.common.ErrorCode;

/**
 * Password checks that annotations cannot express.
 * فحوصات كلمة المرور اللي ما تقدر الـ annotations تعبّر عنها.
 */
final class PasswordRules {

    // BCrypt only reads the first 72 BYTES. Arabic letters take 2 bytes, so 72 characters can exceed it.
    // BCrypt يقرأ أول 72 بايت فقط. والحرف العربي ياخذ بايتين، فـ72 حرف ممكن تتجاوز الحد.
    static final int BCRYPT_MAX_BYTES = 72;

    private PasswordRules() {
    }

    static boolean tooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }

    /** Throws the same validation error the annotations produce. / يرمي نفس خطأ التحقق اللي تنتجه الـ annotations. */
    static void requireAcceptable(String password) {
        if (tooLong(password)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("password", "Size")));
        }
    }
}
