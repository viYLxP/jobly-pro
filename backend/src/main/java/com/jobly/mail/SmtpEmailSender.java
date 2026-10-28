package com.jobly.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.jobly.config.AppProperties;

/**
 * Sends through the SMTP server in the spring.mail.* settings (Mailpit in development, a real provider in production).
 * يرسل عبر خادم SMTP في إعدادات spring.mail.* (Mailpit أثناء التطوير، ومزوّد حقيقي في الإنتاج).
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.mail().from();
    }

    @Override
    public void send(String to, String subject, String text, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // "true" = multipart, so we can send a plain-text part and an HTML part together; UTF-8 keeps Arabic intact.
            // "true" = متعدد الأجزاء، فنرسل نص عادي وجزء HTML مع بعض؛ وUTF-8 يحفظ العربي سليم.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build the email: " + e.getMessage(), e);
        }
    }
}
