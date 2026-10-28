package com.jobly.mail;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    Page<EmailOutbox> findByStatus(OutboxStatus status, Pageable pageable);

    /**
     * Picks the emails that are due and locks them. "skip locked" means a second app instance running at the
     * same time takes different rows instead of waiting, so no email is sent twice.
     * يختار الإيميلات المستحقة ويقفلها. "skip locked" يعني لو فيه نسخة ثانية من التطبيق تشتغل بنفس الوقت
     * تاخذ صفوف ثانية بدل ما تنتظر، فما يتكرر إرسال أي إيميل.
     */
    @Query(value = """
            select * from email_outbox
            where status = 'PENDING' and next_attempt_at <= :now
            order by next_attempt_at, id
            limit :batch
            for update skip locked
            """, nativeQuery = true)
    List<EmailOutbox> lockDue(@Param("now") Instant now, @Param("batch") int batch);
}
