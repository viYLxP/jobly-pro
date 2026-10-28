package com.jobly.mail;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds the subject, plain text and HTML of an email from a template, a language and its values.
 * يبني عنوان الإيميل ونسخة النص والـ HTML من قالب ولغة وقيم.
 */
@Component
public class EmailRenderer {

    // {name}, {link}... inside the texts. / {name} و {link}... داخل النصوص.
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final ResourceBundleMessageSource messages = new ResourceBundleMessageSource();

    public EmailRenderer() {
        messages.setBasename("mail/messages");
        // The .properties files are UTF-8, so Arabic is written as real letters, not escape codes.
        // ملفات .properties بترميز UTF-8، فالعربي مكتوب حروف حقيقية وليس رموز escape.
        messages.setDefaultEncoding("UTF-8");
        // Without this, an unknown language would fall back to the server's own language instead of English.
        // بدون هذا، أي لغة غير معروفة ترجع للغة الخادم نفسه بدل الإنجليزي.
        messages.setFallbackToSystemLocale(false);
    }

    public record RenderedEmail(String subject, String text, String html) {
    }

    public RenderedEmail render(EmailTemplate template, String language, Map<String, String> params) {
        Locale locale = Locale.forLanguageTag("ar".equals(language) ? "ar" : "en");
        boolean rtl = "ar".equals(language);
        String prefix = "mail." + template.name() + ".";

        // The subject goes into an email header, so line breaks are removed (header injection).
        // العنوان يدخل في ترويسة الإيميل، فنشيل أسطر جديدة منه (حماية من حقن الترويسات).
        String subject = fill(text(prefix + "subject", locale), params, false).replaceAll("[\\r\\n]+", " ");
        String title = fill(text(prefix + "title", locale), params, false);
        String intro = fill(text(prefix + "intro", locale), params, false);
        String outro = fill(text(prefix + "outro", locale), params, false);
        String button = optionalText(prefix + "button", locale);
        String link = params.get("link");
        String footer = text("mail.footer", locale);
        String brand = text("mail.brand", locale);

        StringBuilder plain = new StringBuilder(title).append("\n\n").append(intro).append("\n");
        if (button != null && link != null) {
            plain.append("\n").append(button).append(": ").append(link).append("\n");
        }
        plain.append("\n").append(outro).append("\n\n--\n").append(brand).append(" - ").append(footer).append("\n");

        String align = rtl ? "right" : "left";
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"").append(rtl ? "ar" : "en").append("\" dir=\"")
                .append(rtl ? "rtl" : "ltr").append("\"><head><meta charset=\"utf-8\"></head>")
                .append("<body style=\"margin:0;background:#f3f5f9;font-family:Tahoma,Arial,sans-serif;color:#1c2540\">")
                .append("<div style=\"max-width:560px;margin:24px auto;background:#ffffff;border-radius:12px;")
                .append("overflow:hidden;text-align:").append(align).append("\">")
                .append("<div style=\"background:#1f4fd8;color:#ffffff;padding:18px 24px;font-size:20px;")
                .append("font-weight:bold\">").append(esc(brand)).append("</div>")
                .append("<div style=\"padding:24px\">")
                .append("<h1 style=\"font-size:20px;margin:0 0 12px\">").append(fill(text(prefix + "title", locale), params, true))
                .append("</h1>")
                .append("<p style=\"line-height:1.7;margin:0 0 20px\">").append(fill(text(prefix + "intro", locale), params, true))
                .append("</p>");
        if (button != null && link != null) {
            html.append("<p style=\"margin:0 0 20px\"><a href=\"").append(esc(link))
                    .append("\" style=\"display:inline-block;background:#1f4fd8;color:#ffffff;padding:12px 22px;")
                    .append("border-radius:8px;text-decoration:none;font-weight:bold\">").append(esc(button))
                    .append("</a></p>")
                    // The raw link too, for mail apps that hide buttons. / والرابط نفسه، لتطبيقات تخفي الأزرار.
                    .append("<p style=\"font-size:12px;color:#5b6784;word-break:break-all;direction:ltr;text-align:left\">")
                    .append(esc(link)).append("</p>");
        }
        html.append("<p style=\"line-height:1.7;margin:0\">").append(fill(text(prefix + "outro", locale), params, true))
                .append("</p></div>")
                .append("<div style=\"padding:14px 24px;font-size:12px;color:#5b6784;background:#f3f5f9\">")
                .append(esc(footer)).append("</div></div></body></html>");

        return new RenderedEmail(subject, plain.toString(), html.toString());
    }

    private String text(String code, Locale locale) {
        return messages.getMessage(code, null, locale);
    }

    private String optionalText(String code, Locale locale) {
        try {
            return messages.getMessage(code, null, locale);
        } catch (NoSuchMessageException e) {
            return null;
        }
    }

    /**
     * Replaces {name}-style placeholders. For HTML, the values are escaped so a name like
     * {@code <script>} cannot inject markup into the email.
     * يستبدل العلامات من نوع {name}. في الـ HTML نهرّب القيم، فاسم مثل {@code <script>} ما يقدر يحقن كود في الإيميل.
     */
    private static String fill(String template, Map<String, String> params, boolean html) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = params.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(html ? esc(value) : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
