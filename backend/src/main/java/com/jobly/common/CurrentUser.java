package com.jobly.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the signed-in user's id from the security context. The token subject is the id, not the email,
 * so the token stays valid if the email ever changes.
 * يقرأ رقم المستخدم المسجّل من سياق الأمان. موضوع الرمز هو الرقم وليس الإيميل،
 * فيبقى الرمز صالحًا لو تغيّر الإيميل.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
