package com.jobly.application;

import java.util.Set;

/**
 * Where an application is in the hiring process, and which moves are allowed.
 * وين وصل الطلب في عملية التوظيف، وأي انتقالات مسموحة.
 *
 * <p>SUBMITTED -> REVIEWING -> INTERVIEW -> OFFER, and REJECTED is possible from any step before the end.
 * OFFER and REJECTED are final, so a candidate is never told "rejected" and then "interview".</p>
 * <p>SUBMITTED -> REVIEWING -> INTERVIEW -> OFFER، والرفض ممكن من أي خطوة قبل النهاية.
 * OFFER و REJECTED نهائيتان، فما يُقال للمرشح "مرفوض" ثم "مقابلة".</p>
 */
public enum ApplicationStatus {
    SUBMITTED, REVIEWING, INTERVIEW, OFFER, REJECTED;

    public Set<ApplicationStatus> next() {
        return switch (this) {
            case SUBMITTED -> Set.of(REVIEWING, REJECTED);
            case REVIEWING -> Set.of(INTERVIEW, REJECTED);
            case INTERVIEW -> Set.of(OFFER, REJECTED);
            case OFFER, REJECTED -> Set.of();
        };
    }

    public boolean canMoveTo(ApplicationStatus target) {
        return next().contains(target);
    }
}
