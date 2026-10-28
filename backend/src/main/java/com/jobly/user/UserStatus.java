package com.jobly.user;

/**
 * Where an account is in its life.
 * وين وصل الحساب في دورة حياته.
 */
public enum UserStatus {
    // Registered, email not verified yet: cannot log in. / مسجّل والإيميل ما انفعّل: ما يقدر يدخل.
    PENDING,
    // Verified and allowed in. / مفعّل ومسموح له يدخل.
    ACTIVE,
    // Blocked by an admin. / موقوف من الأدمن.
    SUSPENDED
}
