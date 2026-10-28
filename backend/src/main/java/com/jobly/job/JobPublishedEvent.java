package com.jobly.job;

/**
 * Announced inside the transaction that publishes a job.
 * يُعلَن داخل عملية نشر الوظيفة.
 */
public record JobPublishedEvent(Job job) {
}
