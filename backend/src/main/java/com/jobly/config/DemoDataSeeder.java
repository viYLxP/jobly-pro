package com.jobly.config;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobly.alert.JobAlert;
import com.jobly.alert.JobAlertRepository;
import com.jobly.application.ApplicationEvent;
import com.jobly.application.ApplicationEventRepository;
import com.jobly.application.ApplicationStatus;
import com.jobly.application.JobApplication;
import com.jobly.application.JobApplicationRepository;
import com.jobly.job.Job;
import com.jobly.job.JobRepository;
import com.jobly.job.JobType;
import com.jobly.profile.CandidateProfile;
import com.jobly.profile.CandidateProfileRepository;
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
    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final CandidateProfileRepository profiles;
    private final JobAlertRepository alerts;

    public DemoDataSeeder(AppProperties properties, AppUserRepository users, PasswordEncoder encoder,
                          JobRepository jobs, JobApplicationRepository applications,
                          ApplicationEventRepository events, CandidateProfileRepository profiles,
                          JobAlertRepository alerts) {
        this.properties = properties;
        this.users = users;
        this.encoder = encoder;
        this.jobs = jobs;
        this.applications = applications;
        this.events = events;
        this.profiles = profiles;
        this.alerts = alerts;
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
        AppUser candidate = users.save(new AppUser("candidate@jobly.example",
                encoder.encode(seed.candidatePassword()), "Noura Alqahtani", Role.CANDIDATE, UserStatus.ACTIVE, "ar", null));
        AppUser najd = users.save(new AppUser("employer@jobly.example", encoder.encode(seed.employerPassword()),
                "Fahad Alharbi", Role.EMPLOYER, UserStatus.ACTIVE, "ar", "Najd Digital"));
        AppUser redSea = users.save(new AppUser("employer2@jobly.example", encoder.encode(seed.employerPassword()),
                "Reem Alghamdi", Role.EMPLOYER, UserStatus.ACTIVE, "en", "Red Sea Logistics"));
        log.info("Created demo accounts and jobs: admin@jobly.example, candidate@jobly.example, employer@jobly.example, "
                + "employer2@jobly.example");

        profiles.save(newProfile(candidate.getId()));
        List<Job> posted = seedJobs(najd, redSea);
        seedApplication(candidate, posted.get(0));
        // A saved search, so the alerts page shows a real example. / بحث محفوظ، حتى تعرض صفحة التنبيهات مثال حقيقي.
        alerts.save(new JobAlert(candidate, "java", "Riyadh", null));
    }

    private static CandidateProfile newProfile(Long userId) {
        CandidateProfile profile = new CandidateProfile(userId);
        profile.update("Java developer", "Riyadh", 4,
                "Four years building Spring Boot services for banks and retail. Looking for a senior role.");
        return profile;
    }

    /**
     * A believable job board: published jobs on different days in several cities, and one draft.
     * لوحة وظائف مقنعة: وظائف منشورة بأيام مختلفة في عدة مدن، ومسودة وحدة.
     */
    private List<Job> seedJobs(AppUser najd, AppUser redSea) {
        Instant now = Instant.now();
        List<Job> list = List.of(
                job(najd, "Senior Java Developer", "Riyadh", JobType.FULL_TIME, 18000, 25000, 1, now,
                        "Build and run Spring Boot services on PostgreSQL for our fintech clients. You will review code, "
                                + "mentor two developers and own the quality of what ships."),
                job(najd, "Angular Frontend Developer", "Riyadh", JobType.FULL_TIME, 14000, 20000, 2, now,
                        "Build Arabic and English interfaces with Angular, including right-to-left layouts, for our web products."),
                job(najd, "UI/UX Designer", "Jeddah", JobType.CONTRACT, 10000, 15000, 3, now,
                        "Design clear, accessible screens for Arabic-first products. A six month contract with a likely extension."),
                job(najd, "Data Analyst", "Dammam", JobType.FULL_TIME, 12000, 17000, 4, now,
                        "Turn operations data into weekly dashboards and decisions for our energy-sector customers."),
                job(najd, "QA Engineer", "Riyadh", JobType.FULL_TIME, 9000, 13000, 5, now,
                        "Write automated browser and API tests and keep releases boring."),
                job(najd, "Software Engineering Intern", "Riyadh", JobType.INTERNSHIP, 3000, 4000, 6, now,
                        "A three month paid internship working beside our engineers on a real product."),
                job(najd, "DevOps Engineer", "Riyadh", JobType.FULL_TIME, 15000, 21000, 0, now,
                        "Draft: containers, CI/CD and monitoring. Not published yet."),
                job(redSea, "Logistics Coordinator", "Jeddah", JobType.FULL_TIME, 8000, 11000, 1, now,
                        "Coordinate shipments between the port, warehouses and customers across the western region."),
                job(redSea, "Customer Support Agent", "Jeddah", JobType.PART_TIME, 4000, 6000, 3, now,
                        "Answer customer questions by phone and chat in Arabic and English, evening shifts."),
                job(redSea, "Warehouse Supervisor", "Dammam", JobType.FULL_TIME, 9000, 12000, 5, now,
                        "Lead a team of twelve and keep stock accurate and orders on time."),
                job(redSea, "Procurement Specialist", "Riyadh", JobType.FULL_TIME, 11000, 15000, 7, now,
                        "Negotiate with suppliers and manage purchase orders for our regional network."));
        return jobs.saveAll(list);
    }

    // daysAgo = 0 leaves the job as a draft. / daysAgo = 0 يترك الوظيفة مسودة.
    private static Job job(AppUser employer, String title, String city, JobType type, int min, int max,
                           int daysAgo, Instant now, String description) {
        Job job = new Job(employer, title, description, city, type, min, max);
        if (daysAgo > 0) {
            job.publish(now.minus(Duration.ofDays(daysAgo)));
        }
        return job;
    }

    /** One application already under review, so the candidate's page is not empty on the first visit. / طلب قيد المراجعة من البداية، حتى صفحة المرشح ما تكون فاضية. */
    private void seedApplication(AppUser candidate, Job job) {
        JobApplication application = new JobApplication(job, candidate,
                "I have shipped Spring Boot services for four years and would love to join your team.");
        application = applications.save(application);
        Instant submitted = application.getCreatedAt();
        events.save(new ApplicationEvent(application.getId(), ApplicationStatus.SUBMITTED, submitted));
        Instant reviewing = submitted.plusSeconds(3600);
        application.moveTo(ApplicationStatus.REVIEWING, reviewing);
        events.save(new ApplicationEvent(application.getId(), ApplicationStatus.REVIEWING, reviewing));
    }
}
