package com.jobly.config;

import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.jobly.security.ProblemAccessDeniedHandler;
import com.jobly.security.ProblemAuthenticationEntryPoint;
import com.jobly.user.AppUser;
import com.jobly.user.AppUserRepository;
import com.jobly.user.UserStatus;

/**
 * Security setup: stateless JWT authentication. Roles are CANDIDATE, EMPLOYER and ADMIN; visitors have no token.
 * إعداد الأمان: مصادقة JWT بدون جلسات (stateless). الأدوار CANDIDATE و EMPLOYER و ADMIN؛ والزائر بدون رمز.
 *
 * <p>We use Spring Security's built-in OAuth2 Resource Server support to validate tokens instead of a
 * hand-written filter: it checks the signature and expiry for us and is well tested.</p>
 * <p>نستخدم دعم OAuth2 Resource Server المدمج في Spring Security للتحقق من الرموز بدل كتابة فلتر يدوي:
 * يتحقق من التوقيع والانتهاء بنفسه ومختبر جيدًا.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Name of the JWT claim that holds the user's role.
    // اسم الحقل داخل رمز JWT الذي يحمل صلاحية المستخدم.
    public static final String ROLE_CLAIM = "role";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ProblemAuthenticationEntryPoint entryPoint,
                                            ProblemAccessDeniedHandler accessDeniedHandler,
                                            AppUserRepository users) throws Exception {
        http
                // CSRF protection is for cookie sessions; we send the token in the Authorization header, so it is not needed.
                // حماية CSRF مخصصة لجلسات الكوكيز، ونحن نرسل الرمز في ترويسة Authorization فلا نحتاجها.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                // Never create an HTTP session: every request must carry its own token.
                // لا ننشئ جلسة HTTP أبدًا: كل طلب لازم يحمل رمزه.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public account endpoints, listed one by one so a new endpoint is never public by accident.
                        // روابط الحسابات العامة، مكتوبة وحدة وحدة حتى ما يصير رابط جديد عام بالغلط.
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/verify-email",
                                "/api/auth/resend-verification", "/api/auth/login", "/api/auth/forgot-password",
                                "/api/auth/reset-password", "/api/public/unsubscribe").permitAll()
                        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // "Who am I" works for every signed-in account, so the UI knows where to send it.
                        // "مين أنا" يشتغل لكل حساب مسجّل، حتى الواجهة تعرف وين توديه.
                        .requestMatchers("/api/auth/me").authenticated()
                        // Anyone, signed in or not, may search and read jobs.
                        // أي أحد، مسجّل أو لا، يقدر يبحث ويقرأ الوظائف.
                        .requestMatchers(HttpMethod.GET, "/api/jobs", "/api/jobs/*").permitAll()
                        // Each role gets its own area, so a candidate token can never reach employer data and back.
                        // كل دور له منطقته، فرمز المرشح ما يوصل لبيانات صاحب العمل والعكس.
                        .requestMatchers("/api/candidate/**").hasRole("CANDIDATE")
                        .requestMatchers("/api/employer/**").hasRole("EMPLOYER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Everything else is closed until a later step opens it with an explicit rule.
                        // كل شي ثاني مقفل لين تفتحه خطوة لاحقة بقاعدة صريحة.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(users)))
                        // Return our ProblemDetail JSON instead of an empty 401 body.
                        // نرجّع JSON بصيغة ProblemDetail بدل رد 401 فاضي.
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * Turns a valid token into the logged-in user, using the account as it is NOW in the database.
     * يحوّل الرمز الصالح للمستخدم المسجّل، بحسب حالة الحساب الحين في قاعدة البيانات.
     *
     * <p>A token lives for hours. Reading the account on each request means a suspended user is locked out at once,
     * a role change applies immediately, and a password change kills every older token. It costs one lookup on the
     * primary key per request.</p>
     * <p>الرمز يعيش ساعات. قراءة الحساب مع كل طلب تعني إن الموقوف ينقفل عليه فورًا، وتغيير الدور يطبق مباشرة،
     * وتغيير كلمة المرور يقتل كل الرموز الأقدم. والتكلفة استعلام واحد على المفتاح الأساسي لكل طلب.</p>
     */
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(AppUserRepository users) {
        return jwt -> {
            AppUser user = parseId(jwt.getSubject()).flatMap(users::findById)
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                    // The token stores whole seconds, so compare at second precision: a token issued in the same
                    // second as the change is accepted, an older one is not.
                    // الرمز يخزّن ثواني كاملة، فنقارن بدقة الثانية: رمز صدر بنفس ثانية التغيير يُقبل، والأقدم لا.
                    .filter(u -> u.getPasswordChangedAt() == null
                            || !jwt.getIssuedAt().isBefore(u.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS)))
                    // An AuthenticationException becomes a 401, so the UI sends the user back to the login page.
                    // AuthenticationException يتحول لـ 401، فالواجهة ترجّع المستخدم لصفحة الدخول.
                    .orElseThrow(() -> new DisabledException("Account is inactive, removed or the token is outdated"));
            return new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())), String.valueOf(user.getId()));
        };
    }

    private static java.util.Optional<Long> parseId(String subject) {
        try {
            return java.util.Optional.of(Long.parseLong(subject));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Builds the HMAC-SHA256 signing key from the configured secret.
     * يبني مفتاح التوقيع HMAC-SHA256 من السر الموجود في الإعدادات.
     */
    @Bean
    SecretKey jwtSecretKey(AppProperties properties) {
        byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    // Signs new tokens at login.
    // يوقّع الرموز الجديدة عند تسجيل الدخول.
    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    // Verifies the signature and expiry of incoming tokens.
    // يتحقق من توقيع وانتهاء الرموز القادمة مع كل طلب.
    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    // BCrypt hashes passwords with a random salt; plain passwords are never stored.
    // BCrypt يشفّر كلمات المرور مع salt عشوائي، ولا نخزّن كلمة المرور الأصلية أبدًا.
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Lets the Angular app (another origin in development) call the API from the browser.
    // يسمح لتطبيق Angular (مصدر مختلف أثناء التطوير) بالاتصال بالـ API من المتصفح.
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
