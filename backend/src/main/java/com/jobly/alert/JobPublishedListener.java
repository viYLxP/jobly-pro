package com.jobly.alert;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jobly.job.JobPublishedEvent;

/**
 * Connects "a job was published" to the alert emails, without the job code knowing alerts exist.
 * يربط "نُشرت وظيفة" بإيميلات التنبيه، بدون ما يعرف كود الوظائف إن فيه تنبيهات.
 *
 * <p>A plain {@code @EventListener} runs in the publisher's transaction, which is exactly what we want.</p>
 * <p>{@code @EventListener} العادي يشتغل داخل عملية الناشر، وهذا بالضبط اللي نبيه.</p>
 */
@Component
class JobPublishedListener {

    private final AlertService alerts;

    JobPublishedListener(AlertService alerts) {
        this.alerts = alerts;
    }

    @EventListener
    void onJobPublished(JobPublishedEvent event) {
        alerts.notifyMatching(event.job());
    }
}
