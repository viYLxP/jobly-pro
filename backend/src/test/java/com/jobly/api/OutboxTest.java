package com.jobly.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import com.jobly.mail.EmailTemplate;
import com.jobly.mail.OutboxService;
import com.jobly.support.IntegrationTest;

/**
 * The email outbox: emails survive a mail-server outage, retry with a delay, and give up after the limit.
 * صندوق الإيميلات: الإيميلات تنجو من تعطل خادم البريد، وتعيد المحاولة بتأخير، وتستسلم عند الحد.
 */
class OutboxTest extends IntegrationTest {

    @Autowired
    private OutboxService outboxService;

    private String outboxStatus() {
        return jdbc.queryForObject("select status from email_outbox order by id limit 1", String.class);
    }

    private int attempts() {
        return jdbc.queryForObject("select attempts from email_outbox order by id limit 1", Integer.class);
    }

    private void makeDueNow() {
        jdbc.update("update email_outbox set next_attempt_at = now() - interval '1 second' where status = 'PENDING'");
    }

    @Test
    @DisplayName("the email is queued even while the mail server is down, and delivered when it is back")
    void survivesOutage() throws Exception {
        GREEN_MAIL.stop();
        try {
            // Registration itself succeeds: it only writes to the outbox.
            // التسجيل نفسه ينجح: هو يكتب في الصندوق فقط.
            registerCandidate("sara@example.com").andExpect(status().isAccepted());
            assertThat(outboxProcessor.processDue()).isZero();
            assertThat(outboxStatus()).isEqualTo("PENDING");
            assertThat(attempts()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select last_error from email_outbox", String.class)).isNotBlank();
            // The retry is scheduled in the future, so a second run right away does nothing.
            // إعادة المحاولة مجدولة بالمستقبل، فتشغيل ثاني مباشرة ما يسوي شي.
            assertThat(outboxProcessor.processDue()).isZero();
            assertThat(attempts()).isEqualTo(1);
        } finally {
            GREEN_MAIL.start();
        }

        makeDueNow();
        assertThat(outboxProcessor.processDue()).isEqualTo(1);
        assertThat(outboxStatus()).isEqualTo("SENT");
        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
    }

    @Test
    @DisplayName("the retry delay doubles with every failure")
    void backoffGrows() throws Exception {
        GREEN_MAIL.stop();
        try {
            registerCandidate("sara@example.com").andExpect(status().isAccepted());
            outboxProcessor.processDue();
            long firstDelay = secondsUntilNextAttempt();
            makeDueNow();
            outboxProcessor.processDue();
            long secondDelay = secondsUntilNextAttempt();

            // 30s then 60s (allow a second of clock movement). / 30 ثانية ثم 60 (نسمح بثانية فرق للساعة).
            assertThat(firstDelay).isBetween(28L, 30L);
            assertThat(secondDelay).isBetween(58L, 60L);
        } finally {
            GREEN_MAIL.start();
        }
    }

    @Test
    @DisplayName("after 5 failed attempts the email is marked FAILED and is not tried again")
    void givesUp() throws Exception {
        GREEN_MAIL.stop();
        try {
            registerCandidate("sara@example.com").andExpect(status().isAccepted());
            for (int i = 0; i < 5; i++) {
                makeDueNow();
                outboxProcessor.processDue();
            }
            assertThat(outboxStatus()).isEqualTo("FAILED");
            assertThat(attempts()).isEqualTo(5);
        } finally {
            GREEN_MAIL.start();
        }

        // Even with the server back, a FAILED email stays failed. / حتى لو رجع الخادم، الإيميل الفاشل يبقى فاشل.
        assertThat(outboxProcessor.processDue()).isZero();
        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("an email is sent exactly once, even if the sender runs twice")
    void sentOnce() throws Exception {
        registerCandidate("sara@example.com").andExpect(status().isAccepted());

        assertThat(outboxProcessor.processDue()).isEqualTo(1);
        assertThat(outboxProcessor.processDue()).isZero();
        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
    }

    @Test
    @DisplayName("a rejected registration queues no email")
    void noEmailWithoutChange() throws Exception {
        register("bad", PASSWORD, "X", "CANDIDATE", null, "en").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from email_outbox", Integer.class)).isZero();
    }

    @Test
    @DisplayName("queuing an email outside a transaction is refused, so an email can never exist without its change")
    void enqueueNeedsATransaction() {
        assertThatThrownBy(() -> outboxService.enqueue("a@example.com", "en", EmailTemplate.WELCOME, Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private long secondsUntilNextAttempt() {
        return jdbc.queryForObject(
                "select extract(epoch from (next_attempt_at - now()))::bigint from email_outbox order by id limit 1",
                Long.class);
    }
}
