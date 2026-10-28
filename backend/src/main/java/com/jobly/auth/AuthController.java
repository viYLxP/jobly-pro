package com.jobly.auth;

import jakarta.validation.Valid;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobly.auth.AuthDtos.EmailRequest;
import com.jobly.auth.AuthDtos.LoginRequest;
import com.jobly.auth.AuthDtos.LoginResponse;
import com.jobly.auth.AuthDtos.RegisterRequest;
import com.jobly.auth.AuthDtos.ResetRequest;
import com.jobly.auth.AuthDtos.StatusResponse;
import com.jobly.auth.AuthDtos.VerifyRequest;
import com.jobly.common.ApiException;
import com.jobly.common.CurrentUser;
import com.jobly.user.AppUserRepository;
import com.jobly.user.UserDto;

/**
 * Account endpoints: register, verify email, login, password reset, and "who am I".
 * روابط الحسابات: تسجيل، تفعيل إيميل، دخول، استعادة كلمة المرور، و"مين أنا".
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final AppUserRepository users;

    public AuthController(AuthService service, AppUserRepository users) {
        this.service = service;
        this.users = users;
    }

    // 202 Accepted: the real work (sending the email) happens later. The body is the same for new and existing emails.
    // 202 Accepted: الشغل الفعلي (إرسال الإيميل) يصير لاحقًا. والرد نفسه للإيميل الجديد والموجود.
    @PostMapping("/register")
    ResponseEntity<StatusResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            service.register(request);
        } catch (DataIntegrityViolationException e) {
            // Two registrations with the same new email at the same instant: the database unique index let one
            // win. For the loser we answer exactly like a success, so the race does not reveal the email exists.
            // تسجيلين بنفس الإيميل الجديد بنفس اللحظة: فهرس الفرادة خلّى واحد يفوز. للخاسر نرد مثل النجاح،
            // حتى السباق ما يكشف إن الإيميل موجود.
        }
        return accepted("CHECK_EMAIL");
    }

    @PostMapping("/verify-email")
    StatusResponse verifyEmail(@Valid @RequestBody VerifyRequest request) {
        return new StatusResponse(service.verifyEmail(request.token()).name());
    }

    @PostMapping("/resend-verification")
    ResponseEntity<StatusResponse> resendVerification(@Valid @RequestBody EmailRequest request) {
        service.resendVerification(request.email());
        return accepted("CHECK_EMAIL");
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.email(), request.password());
    }

    @PostMapping("/forgot-password")
    ResponseEntity<StatusResponse> forgotPassword(@Valid @RequestBody EmailRequest request) {
        service.forgotPassword(request.email());
        return accepted("CHECK_EMAIL");
    }

    @PostMapping("/reset-password")
    StatusResponse resetPassword(@Valid @RequestBody ResetRequest request) {
        service.resetPassword(request.token(), request.password());
        return new StatusResponse("PASSWORD_CHANGED");
    }

    // Lets the app learn the role of the signed-in user, to decide which screens to show.
    // يخلي التطبيق يعرف دور المستخدم المسجّل ليقرر أي شاشات يعرض.
    @GetMapping("/me")
    UserDto me() {
        return users.findById(CurrentUser.id()).map(UserDto::of)
                .orElseThrow(() -> ApiException.notFound("user", CurrentUser.id()));
    }

    private static ResponseEntity<StatusResponse> accepted(String status) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new StatusResponse(status));
    }
}
