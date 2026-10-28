package com.jobly.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Creates the random secrets we put in email links, and hashes them for storage.
 * ينشئ الأسرار العشوائية اللي نضعها في روابط الإيميل، ويحوّلها لبصمة للتخزين.
 */
public final class SecureTokens {

    // SecureRandom is thread-safe and seeded by the operating system.
    // SecureRandom آمن للاستخدام من عدة خيوط ويأخذ بذرته من نظام التشغيل.
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /**
     * 32 random bytes (256 bits) as URL-safe text, which cannot be guessed.
     * 32 بايت عشوائي (256 بت) كنص آمن للروابط، ولا يمكن تخمينه.
     */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 as 64 hex characters. We store this, never the token itself. A fast hash is fine here because the
     * input is 256 random bits, so brute force is impossible (unlike a password).
     * SHA-256 بـ 64 حرف hex. نخزّنه هو، وليس الرمز نفسه. التشفير السريع كافٍ هنا لأن المدخل 256 بت عشوائية،
     * فالتخمين مستحيل (بخلاف كلمة المرور).
     */
    public static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform must provide SHA-256, so this cannot happen.
            // كل منصات Java لازم توفر SHA-256، فهذا ما يصير.
            throw new IllegalStateException(e);
        }
    }
}
