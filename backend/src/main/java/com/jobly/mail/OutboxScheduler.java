package com.jobly.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the outbox sender in the background every few seconds.
 * يشغّل مرسل الطابور في الخلفية كل كم ثانية.
 *
 * <p>Turned off in tests (jobly.mail.scheduler-enabled=false) so they control exactly when emails go out.</p>
 * <p>يتوقف في الاختبارات (jobly.mail.scheduler-enabled=false) حتى تتحكم هي بوقت خروج الإيميلات بالضبط.</p>
 */
@Component
@ConditionalOnProperty(name = "jobly.mail.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxProcessor processor;

    public OutboxScheduler(OutboxProcessor processor) {
        this.processor = processor;
    }

    // fixedDelay waits for the previous run to finish, so two runs never overlap in one instance.
    // fixedDelay ينتظر انتهاء الدورة السابقة، فما تتداخل دورتان في نفس النسخة.
    @Scheduled(fixedDelayString = "${jobly.mail.poll-interval}")
    void run() {
        try {
            processor.processDue();
        } catch (Exception e) {
            // e.g. the database is briefly unavailable; the next run tries again.
            // مثل انقطاع قاعدة البيانات لحظة؛ والدورة التالية تعيد المحاولة.
            log.warn("Outbox run failed: {}", e.getMessage());
        }
    }
}
