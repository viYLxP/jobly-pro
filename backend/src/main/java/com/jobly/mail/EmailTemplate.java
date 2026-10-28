package com.jobly.mail;

/**
 * Every kind of email the app sends. The texts live in resources/mail/messages*.properties.
 * كل أنواع الإيميلات اللي يرسلها التطبيق. النصوص في resources/mail/messages*.properties.
 */
public enum EmailTemplate {
    // Link to activate a new account. / رابط تفعيل حساب جديد.
    VERIFY_EMAIL,
    // Sent right after the email is verified. / يُرسل مباشرة بعد تفعيل الإيميل.
    WELCOME,
    // Link to choose a new password. / رابط اختيار كلمة مرور جديدة.
    RESET_PASSWORD,
    // Security notice after a password change. / تنبيه أمني بعد تغيير كلمة المرور.
    PASSWORD_CHANGED,
    // Someone registered with an email that already has an account. / أحدهم سجّل بإيميل عنده حساب فعلًا.
    ACCOUNT_EXISTS
}
