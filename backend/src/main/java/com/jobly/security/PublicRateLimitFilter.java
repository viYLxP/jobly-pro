package com.jobly.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jobly.config.AppProperties;

/**
 * Limits the public account endpoints per visitor IP: guessing passwords or email links, and using the app to
 * send many emails.
 * يحدّ روابط الحسابات العامة لكل عنوان IP: تخمين كلمات المرور أو روابط الإيميل، واستخدام التطبيق لإرسال إيميلات كثيرة.
 *
 * <p>The IP comes from request.getRemoteAddr(). With server.forward-headers-strategy=native, Tomcat replaces it with
 * the X-Forwarded-For value only when the request came through a trusted internal proxy.</p>
 * <p>العنوان من request.getRemoteAddr(). ومع server.forward-headers-strategy=native، Tomcat يبدله بقيمة
 * X-Forwarded-For بس لو الطلب جا من بروكسي داخلي موثوق.</p>
 */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/verify-email",
            "/api/auth/resend-verification",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/public/unsubscribe");

    private final RateLimiter attempts;

    public PublicRateLimitFilter(AppProperties properties) {
        AppProperties.RateLimit config = properties.rateLimit();
        this.attempts = new RateLimiter(config.attempts(), config.window(), System::currentTimeMillis);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = attempts.hit(request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"code\":\"RATE_LIMITED\","
                    + "\"retryAfterSeconds\":" + retryAfter + "}");
            return;
        }
        chain.doFilter(request, response);
    }
}
