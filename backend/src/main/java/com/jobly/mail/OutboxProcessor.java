package com.jobly.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobly.config.AppProperties;

/**
 * Delivers the emails that are due. Failed ones are retried later with a growing delay, and after too many
 * failures they are marked FAILED and left alone.
 * يوصّل الإيميلات المستحقة. اللي تفشل تنعاد لاحقًا بتأخير يكبر، وبعد فشل كثير تنعلّم FAILED وتنترك.
 */
@Service
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final EmailOutboxRepository outbox;
    private final EmailRenderer renderer;
    private final EmailSender sender;
    private final AppProperties.Mail settings;

    public OutboxProcessor(EmailOutboxRepository outbox, EmailRenderer renderer, EmailSender sender,
                           AppProperties properties) {
        this.outbox = outbox;
        this.renderer = renderer;
        this.sender = sender;
        this.settings = properties.mail();
    }

    /**
     * Handles one batch and returns how many emails were delivered. Public so tests can run it on demand.
     * يعالج دفعة واحدة ويرجّع كم إيميل انوصّل. عام عشان الاختبارات تشغّله وقت ما تبي.
     */
    @Transactional
    public int processDue() {
        Instant now = Instant.now();
        List<EmailOutbox> due = outbox.lockDue(now, settings.batchSize());
        int delivered = 0;
        for (EmailOutbox email : due) {
            try {
                EmailRenderer.RenderedEmail rendered =
                        renderer.render(email.getTemplate(), email.getLanguage(), email.getParams());
                sender.send(email.getToEmail(), rendered.subject(), rendered.text(), rendered.html());
                email.markSent(Instant.now());
                delivered++;
            } catch (Exception e) {
                // One bad email must not stop the others in the batch. / إيميل خربان ما يوقف باقي الدفعة.
                int attempt = email.getAttempts() + 1;
                boolean giveUp = attempt >= settings.maxAttempts();
                // Delay doubles each time: 30s, 60s, 120s... (capped at 2^10 steps so it cannot overflow).
                // التأخير يتضاعف كل مرة: 30 ثانية، 60، 120... (بحد أعلى حتى ما يتجاوز).
                Duration delay = settings.retryDelay().multipliedBy(1L << Math.min(attempt - 1, 10));
                email.markFailed(e.getMessage(), Instant.now().plus(delay), giveUp);
                log.warn("Email {} to {} failed (attempt {}{}): {}", email.getId(), email.getToEmail(), attempt,
                        giveUp ? ", giving up" : "", e.getMessage());
            }
        }
        return delivered;
    }
}
