package com.jobly.job;

/**
 * Life of a job posting: DRAFT -> PUBLISHED -> CLOSED.
 * دورة حياة إعلان الوظيفة: مسودة -> منشورة -> مغلقة.
 */
public enum JobStatus {
    // Only its employer sees it. / صاحبها فقط يشوفها.
    DRAFT,
    // Searchable and open for applications. / تظهر في البحث ومفتوحة للتقديم.
    PUBLISHED,
    // Still viewable by link, but takes no more applications. / تبقى ظاهرة بالرابط لكن ما تستقبل طلبات.
    CLOSED
}
