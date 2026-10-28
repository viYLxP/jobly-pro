package com.jobly.mail;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way the rest of the app asks for an email to be sent.
 * الطريقة الوحيدة اللي يطلب فيها باقي التطبيق إرسال إيميل.
 */
@Service
public class OutboxService {

    private final EmailOutboxRepository outbox;

    public OutboxService(EmailOutboxRepository outbox) {
        this.outbox = outbox;
    }

    /**
     * Queues an email. MANDATORY means it must run inside the caller's transaction: if the business change rolls
     * back, the email is not queued either.
     * يضيف إيميل للطابور. MANDATORY يعني لازم يشتغل داخل عملية المستدعي: لو تراجع التغيير ما ينضاف الإيميل.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String toEmail, String language, EmailTemplate template, Map<String, String> params) {
        outbox.save(new EmailOutbox(toEmail, language, template, params));
    }
}
