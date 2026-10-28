package com.jobly.common;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned by the API.
 * رموز أخطاء ثابتة يرجّعها الـ API ويفهمها البرنامج.
 *
 * <p>The frontend translates these codes into Arabic or English, so the API never returns
 * user-facing text. This keeps server logic independent of the display language.</p>
 * <p>الواجهة الأمامية تترجم هذه الرموز إلى العربية أو الإنجليزية، فلا يرجّع الـ API أي نص
 * موجّه للمستخدم. هذا يفصل منطق الخادم عن لغة العرض.</p>
 */
public enum ErrorCode {
    // Request data is invalid (missing field, bad email, weak password, malformed JSON...).
    // بيانات الطلب غير صحيحة (حقل ناقص، إيميل خطأ، كلمة مرور ضعيفة، JSON تالف...).
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    // Wrong email or password. The same answer for "no such account", so nobody can probe which emails exist.
    // إيميل أو كلمة مرور خطأ. نفس الرد لو الحساب غير موجود، حتى ما أحد يكتشف أي إيميل مسجّل.
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    // The password was right but the email was never verified. Sent only after a correct password.
    // كلمة المرور صحيحة لكن الإيميل ما انفعّل. ما نرجّعه إلا بعد كلمة مرور صحيحة.
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    // The account was suspended by an admin. / الحساب موقوف من الأدمن.
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN),
    // Logged in, but the role does not allow this action.
    // المستخدم مسجّل دخول لكن صلاحيته لا تسمح بالعملية.
    FORBIDDEN(HttpStatus.FORBIDDEN),
    // The requested record does not exist.
    // السجل المطلوب غير موجود.
    NOT_FOUND(HttpStatus.NOT_FOUND),
    // The email link is wrong, already used, or replaced by a newer one. / رابط الإيميل غلط، أو مستخدم، أو انستبدل بأحدث.
    TOKEN_INVALID(HttpStatus.BAD_REQUEST),
    // The email link is correct but expired, so the UI offers to send a new one. / الرابط صحيح لكنه انتهى، فتعرض الواجهة إرسال رابط جديد.
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST),
    // Too many requests from the same address on a public endpoint. / طلبات كثيرة من نفس العنوان على رابط عام.
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    // Someone else changed the same record meanwhile (optimistic locking).
    // شخص آخر عدّل نفس السجل في نفس الوقت (Optimistic Locking).
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),
    // Cannot delete because other records reference it.
    // لا يمكن الحذف لأن سجلات أخرى مرتبطة به.
    IN_USE(HttpStatus.CONFLICT),
    // Unexpected server error.
    // خطأ غير متوقع في الخادم.
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    // The HTTP status for each code, kept in one place.
    // حالة HTTP المناسبة لكل رمز، محفوظة في مكان واحد.
    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
