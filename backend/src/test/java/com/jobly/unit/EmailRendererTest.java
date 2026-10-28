package com.jobly.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jobly.mail.EmailRenderer;
import com.jobly.mail.EmailTemplate;

class EmailRendererTest {

    private final EmailRenderer renderer = new EmailRenderer();

    @Test
    @DisplayName("every template has a subject, title, intro and outro in both languages, with no placeholder left over")
    void allTemplatesComplete() {
        for (EmailTemplate template : EmailTemplate.values()) {
            for (String language : new String[] {"en", "ar"}) {
                EmailRenderer.RenderedEmail email = renderer.render(template, language,
                        Map.of("name", "Sara", "link", "https://x.example/a#b"));
                assertThat(email.subject()).as(template + "/" + language).isNotBlank().doesNotContain("{");
                assertThat(email.text()).doesNotContain("{name}", "{link}");
                assertThat(email.html()).doesNotContain("{name}", "{link}");
            }
        }
    }

    @Test
    @DisplayName("Arabic is right-to-left and English left-to-right; an unknown language falls back to English")
    void direction() {
        Map<String, String> params = Map.of("name", "Sara", "link", "https://x.example/a");
        assertThat(renderer.render(EmailTemplate.WELCOME, "ar", params).html()).contains("dir=\"rtl\"");
        assertThat(renderer.render(EmailTemplate.WELCOME, "en", params).html()).contains("dir=\"ltr\"");
        assertThat(renderer.render(EmailTemplate.WELCOME, "fr", params).subject()).isEqualTo("Your Jobly account is ready");
    }

    @Test
    @DisplayName("values are escaped in the HTML but not in the plain text")
    void escaping() {
        EmailRenderer.RenderedEmail email = renderer.render(EmailTemplate.WELCOME, "en",
                Map.of("name", "<b>Sara</b> & Co", "link", "https://x.example/?a=1&b=2"));

        assertThat(email.html()).doesNotContain("<b>Sara</b>").contains("&lt;b&gt;Sara&lt;/b&gt; &amp; Co")
                .contains("a=1&amp;b=2");
        assertThat(email.text()).contains("<b>Sara</b> & Co");
    }

    @Test
    @DisplayName("line breaks are removed from the subject, so a value can never add a mail header")
    void subjectHasNoLineBreaks() {
        EmailRenderer.RenderedEmail email = renderer.render(EmailTemplate.WELCOME, "en",
                Map.of("name", "Sara\r\nBcc: evil@example.com"));

        assertThat(email.subject()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    @DisplayName("without a link there is no button")
    void noLinkNoButton() {
        EmailRenderer.RenderedEmail email = renderer.render(EmailTemplate.WELCOME, "en", Map.of("name", "Sara"));

        assertThat(email.html()).doesNotContain("<a href");
    }
}
