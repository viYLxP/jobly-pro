package com.jobly.mail;

/**
 * Delivers one ready email. A separate interface keeps the outbox logic independent of SMTP.
 * يوصّل إيميل جاهز واحد. واجهة منفصلة تخلي منطق الطابور مستقل عن SMTP.
 */
public interface EmailSender {

    /** Throws if the message could not be delivered, so the outbox retries later. / يرمي استثناء لو فشل التوصيل، فيعيد الطابور المحاولة. */
    void send(String to, String subject, String text, String html);
}
