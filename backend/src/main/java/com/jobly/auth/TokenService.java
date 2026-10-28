package com.jobly.auth;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.jobly.config.AppProperties;
import com.jobly.config.SecurityConfig;
import com.jobly.user.AppUser;

/**
 * Issues signed JWT access tokens.
 * يصدر رموز JWT موقّعة للدخول.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties properties;

    public TokenService(JwtEncoder encoder, AppProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    /**
     * Creates a token for the user.
     * ينشئ رمزًا للمستخدم.
     */
    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.jwt().expiration());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("jobly-api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                // "sub" holds the account id; Spring exposes it as Authentication.getName().
                // "sub" يحمل رقم الحساب؛ وSpring يعرضه كـ Authentication.getName().
                .subject(String.valueOf(user.getId()))
                .claim(SecurityConfig.ROLE_CLAIM, user.getRole().name())
                .build();
        // HS256 must be set explicitly in the header, otherwise Nimbus cannot pick an algorithm for a secret key.
        // لازم نحدد HS256 صراحة في الترويسة، وإلا ما يقدر Nimbus يختار خوارزمية للمفتاح السري.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
