package com.jobly.alert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.jobly.config.AppProperties;

/**
 * Signed "stop my alerts" tokens for the link in alert emails.
 * رموز موقّعة لرابط "إيقاف تنبيهاتي" في إيميلات التنبيه.
 *
 * <p>The token is the user id plus an HMAC signature made with a server secret, so nobody can create one for
 * another user. It has no expiry on purpose: an unsubscribe link in an old email must keep working. All it can
 * ever do is switch alerts off, so a leaked link is low risk.</p>
 * <p>الرمز هو رقم المستخدم مع توقيع HMAC بمفتاح سري عند الخادم، فما أحد يقدر يصنع رمز لمستخدم ثاني.
 * ما له انتهاء عن قصد: رابط إلغاء الاشتراك في إيميل قديم لازم يظل شغّال. وكل اللي يقدر يسويه هو إيقاف التنبيهات،
 * فتسريب الرابط خطره قليل.</p>
 */
@Component
public class UnsubscribeTokens {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public UnsubscribeTokens(AppProperties properties) {
        // A label in front keeps this key different from the one that signs login tokens.
        // بادئة تخلي هذا المفتاح مختلف عن المفتاح اللي يوقّع رموز الدخول.
        this.key = ("unsubscribe:" + properties.jwt().secret()).getBytes(StandardCharsets.UTF_8);
    }

    public String create(long userId) {
        String id = Long.toString(userId);
        return id + "." + sign(id);
    }

    /** The user id, if the signature is genuine. / رقم المستخدم، لو التوقيع أصلي. */
    public Optional<Long> verify(String token) {
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        String id = token.substring(0, dot);
        // Constant-time comparison, so response time does not leak how much of a guess was right.
        // مقارنة بوقت ثابت، فزمن الرد ما يسرّب كم من التخمين كان صحيح.
        boolean genuine = MessageDigest.isEqual(sign(id).getBytes(StandardCharsets.UTF_8),
                token.substring(dot + 1).getBytes(StandardCharsets.UTF_8));
        if (!genuine) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA256 is available on every Java platform. / HmacSHA256 متوفر في كل منصات Java.
            throw new IllegalStateException(e);
        }
    }
}
