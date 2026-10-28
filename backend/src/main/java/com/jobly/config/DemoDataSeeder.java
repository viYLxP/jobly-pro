package com.jobly.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobly.user.AppUser;
import com.jobly.user.AppUserRepository;
import com.jobly.user.Role;
import com.jobly.user.UserStatus;

/**
 * On an empty database, creates the admin and one demo candidate and employer so the app can be tried at once.
 * على قاعدة فاضية، ينشئ الأدمن ومرشح وصاحب عمل تجريبيين حتى يمكن تجربة التطبيق مباشرة.
 *
 * <p>Runs only when {@code jobly.seed.enabled=true}. Set it to false (and choose real passwords) in production.</p>
 * <p>يشتغل فقط لما {@code jobly.seed.enabled=true}. اخلّه false (واختر كلمات مرور حقيقية) في الإنتاج.</p>
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties properties;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(AppProperties properties, AppUserRepository users, PasswordEncoder encoder) {
        this.properties = properties;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Seed seed = properties.seed();
        // Never touch a database that already has accounts. / ما نلمس قاعدة فيها حسابات أصلًا.
        if (!seed.enabled() || users.count() > 0) {
            return;
        }
        // Demo accounts are created already verified (ACTIVE), as if they had clicked their email link.
        // الحسابات التجريبية تُنشأ مفعّلة (ACTIVE)، كأنها ضغطت رابط الإيميل.
        users.save(new AppUser("admin@jobly.example", encoder.encode(seed.adminPassword()), "Jobly Admin",
                Role.ADMIN, UserStatus.ACTIVE, "en", null));
        users.save(new AppUser("candidate@jobly.example", encoder.encode(seed.candidatePassword()), "Noura Alqahtani",
                Role.CANDIDATE, UserStatus.ACTIVE, "ar", null));
        users.save(new AppUser("employer@jobly.example", encoder.encode(seed.employerPassword()), "Fahad Alharbi",
                Role.EMPLOYER, UserStatus.ACTIVE, "ar", "Najd Digital"));
        log.info("Created demo accounts: admin@jobly.example, candidate@jobly.example, employer@jobly.example");
    }
}
