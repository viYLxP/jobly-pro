package com.jobly.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenHash(String tokenHash);

    // The newest token of a kind, used for the resend cooldown. / أحدث رمز من نوعه، لفترة الانتظار بين الإرسالين.
    Optional<EmailToken> findFirstByUserIdAndTypeOrderByCreatedAtDesc(Long userId, EmailTokenType type);

    // How many were created since a moment, used for the daily limit. / كم رمز انصنع من لحظة معينة، للحد اليومي.
    long countByUserIdAndTypeAndCreatedAtAfter(Long userId, EmailTokenType type, Instant since);

    /**
     * Retires every unused token of this kind, so only the newest email link works.
     * يسحب كل الرموز غير المستخدمة من هذا النوع، فيشتغل أحدث رابط إيميل فقط.
     */
    @Modifying
    @Query("update EmailToken t set t.usedAt = :now where t.user.id = :userId and t.type = :type and t.usedAt is null")
    int invalidateUnused(@Param("userId") Long userId, @Param("type") EmailTokenType type, @Param("now") Instant now);
}
