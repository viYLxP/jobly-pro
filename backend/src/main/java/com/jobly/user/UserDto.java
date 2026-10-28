package com.jobly.user;

/**
 * What the API tells the app about an account. Never includes the password hash.
 * اللي يقوله الـ API للتطبيق عن الحساب. ما يحتوي بصمة كلمة المرور أبدًا.
 */
public record UserDto(long id, String email, String fullName, Role role, UserStatus status, String language,
                      String companyName) {

    public static UserDto of(AppUser user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getStatus(),
                user.getLanguage(), user.getCompanyName());
    }
}
