package com.jobly.mail;

public enum OutboxStatus {
    // Waiting to be sent (or to be retried). / ينتظر الإرسال (أو إعادة المحاولة).
    PENDING,
    SENT,
    // Gave up after too many failures. / توقفنا بعد فشل كثير.
    FAILED
}
