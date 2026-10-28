package com.jobly.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // Emails are stored lower-case; the database index is on lower(email) too, so this is an indexed lookup.
    // الإيميلات مخزّنة بأحرف صغيرة، والفهرس على lower(email)، فالبحث يستخدم الفهرس.
    Optional<AppUser> findByEmailIgnoreCase(String email);
}
