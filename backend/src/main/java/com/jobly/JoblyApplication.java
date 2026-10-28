package com.jobly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Jobly Pro REST API.
 * نقطة تشغيل الواجهة الخلفية (REST API) لتطبيق Jobly Pro.
 *
 * <p>{@code @ConfigurationPropertiesScan} binds the {@code jobly.*} settings in application.yml to
 * {@link com.jobly.config.AppProperties}. {@code @EnableScheduling} lets the email sender run in the background.</p>
 * <p>{@code @ConfigurationPropertiesScan} يربط إعدادات {@code jobly.*} بكلاس AppProperties.
 * و{@code @EnableScheduling} يخلي مرسل الإيميلات يشتغل في الخلفية.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class JoblyApplication {

    public static void main(String[] args) {
        // Boot the whole Spring context: database, Flyway migrations, then the web server.
        // نشغّل سياق Spring كاملًا: قاعدة البيانات، ترحيلات Flyway، ثم خادم الويب.
        SpringApplication.run(JoblyApplication.class, args);
    }
}
