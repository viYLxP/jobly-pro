package com.jobly.common;

import org.springframework.stereotype.Component;

import com.jobly.config.AppProperties;

/**
 * Builds the links we put in emails, always pointing at the web app.
 * يبني الروابط اللي نضعها في الإيميلات، وتشير دائمًا لتطبيق الويب.
 */
@Component
public class Links {

    private final String base;

    public Links(AppProperties properties) {
        String url = properties.publicUrl();
        this.base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * For example {@code url("/login")}. A "#secret" at the end never reaches web servers or their logs, so the
     * secret stays in the browser.
     * مثلًا {@code url("/login")}. الجزء بعد "#" ما يوصل لخوادم الويب ولا سجلاتها، فيبقى السر في المتصفح.
     */
    public String url(String path) {
        return base + path;
    }
}
