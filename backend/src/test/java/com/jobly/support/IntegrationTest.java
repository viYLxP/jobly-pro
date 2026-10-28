package com.jobly.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jobly.mail.OutboxProcessor;
import com.jobly.user.AppUser;
import com.jobly.user.AppUserRepository;
import com.jobly.user.Role;
import com.jobly.user.UserStatus;

/**
 * Base class for integration tests: real Spring context + a REAL PostgreSQL in Docker (Testcontainers) + a REAL
 * SMTP server in memory (GreenMail), so the tests read the emails the app actually sends.
 * الكلاس الأساسي لاختبارات التكامل: سياق Spring حقيقي + PostgreSQL حقيقي داخل Docker (Testcontainers) + خادم SMTP
 * حقيقي في الذاكرة (GreenMail)، فالاختبارات تقرأ الإيميلات اللي يرسلها التطبيق فعلًا.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    // One container shared by all test classes (started once), which keeps the suite fast.
    // حاوية وحدة مشتركة بين كل كلاسات الاختبار (تشتغل مرة وحدة)، وهذا يخلي الاختبارات سريعة.
    // @ServiceConnection points spring.datasource at this container automatically.
    // @ServiceConnection يوجّه إعدادات قاعدة البيانات لهذي الحاوية تلقائيًا.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    // The in-memory SMTP server listens on port 3025, which application-test.yml points the app at.
    // خادم SMTP في الذاكرة يستمع على المنفذ 3025، واللي يوجّه له application-test.yml التطبيق.
    @RegisterExtension
    protected static final GreenMailExtension GREEN_MAIL =
            new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(false);

    protected static final String PASSWORD = "Secret123";

    // The raw secret in an email link is 32 bytes as unpadded base64url = 43 characters, after "#".
    // السر الخام في رابط الإيميل 32 بايت كـ base64url بدون حشو = 43 حرفًا، بعد "#".
    private static final Pattern LINK_TOKEN = Pattern.compile("#([A-Za-z0-9_-]{43})");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected OutboxProcessor outboxProcessor;

    @Autowired
    protected AppUserRepository users;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /**
     * Wipes all data and received emails before each test so tests never depend on each other's leftovers.
     * يمسح كل البيانات والإيميلات المستلمة قبل كل اختبار حتى ما يعتمد أي اختبار على بقايا اختبار ثاني.
     */
    @BeforeEach
    void resetState() throws Exception {
        jdbc.execute("truncate table email_outbox_param, email_outbox, email_token, app_user restart identity cascade");
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    // ----- HTTP helpers / دوال HTTP -----

    protected ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions register(String email, String password, String fullName, String role, String company,
                                     String language) throws Exception {
        String companyJson = company == null ? "null" : "\"" + company + "\"";
        return postJson("/api/auth/register", """
                {"email":"%s","password":"%s","fullName":"%s","role":"%s","companyName":%s,"language":"%s"}
                """.formatted(email, password, fullName, role, companyJson, language));
    }

    /** A plain candidate registration in English. / تسجيل مرشح عادي بالإنجليزي. */
    protected ResultActions registerCandidate(String email) throws Exception {
        return register(email, PASSWORD, "Sara Test", "CANDIDATE", null, "en");
    }

    protected ResultActions loginRequest(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    protected String login(String email, String password) throws Exception {
        String body = loginRequest(email, password).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected ResultActions meWith(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token));
    }

    // ----- Data helpers / دوال البيانات -----

    /** An already verified account, as if the person had clicked the email link. / حساب مفعّل جاهز. */
    protected AppUser activeUser(String email, String password, Role role) {
        return users.save(new AppUser(email, passwordEncoder.encode(password), "Test " + role, role,
                UserStatus.ACTIVE, "en", role == Role.EMPLOYER ? "Test Co" : null));
    }

    /** Pushes the creation time of an account's email links back, so the resend cooldown has passed. / يرجّع وقت إنشاء روابط الحساب للخلف، فتنتهي فترة الانتظار. */
    protected void passCooldown(String email) {
        jdbc.update("update email_token set created_at = created_at - interval '5 minutes' "
                + "where user_id = (select id from app_user where email = ?)", email);
    }

    protected String statusOf(String email) {
        return jdbc.queryForObject("select status from app_user where email = ?", String.class, email);
    }

    // ----- Email helpers / دوال الإيميل -----

    /** Runs the outbox now and returns every email GreenMail has received so far. / يشغّل الطابور الحين ويرجّع كل إيميل وصل GreenMail. */
    protected List<MimeMessage> deliver() {
        outboxProcessor.processDue();
        return new ArrayList<>(Arrays.asList(GREEN_MAIL.getReceivedMessages()));
    }

    /** Delivers and returns the single email sent to this address (fails if there is not exactly one). / يوصّل ويرجّع الإيميل الوحيد لهذا العنوان. */
    protected MimeMessage singleEmailTo(String address) throws Exception {
        List<MimeMessage> all = deliver();
        List<MimeMessage> mine = new ArrayList<>();
        for (MimeMessage message : all) {
            if (Arrays.stream(message.getAllRecipients()).anyMatch(r -> r.toString().contains(address))) {
                mine.add(message);
            }
        }
        if (mine.size() != 1) {
            throw new AssertionError("Expected exactly 1 email to " + address + " but found " + mine.size());
        }
        return mine.get(0);
    }

    /** The secret from the link in an email (the part after "#"). / السر من الرابط في الإيميل (الجزء بعد "#"). */
    protected static String tokenIn(MimeMessage message) throws Exception {
        Matcher matcher = LINK_TOKEN.matcher(partOf(message, "text/plain"));
        if (!matcher.find()) {
            throw new AssertionError("No link token found in the email");
        }
        return matcher.group(1);
    }

    protected static String plainText(MimeMessage message) throws Exception {
        return partOf(message, "text/plain");
    }

    protected static String html(MimeMessage message) throws Exception {
        return partOf(message, "text/html");
    }

    /**
     * Reads the decoded text of the first part with this type. GreenMail keeps the message encoded
     * (quoted-printable / base64), and the library decodes it for us here.
     * يقرأ النص المفكوك لأول جزء بهذا النوع. GreenMail يحفظ الرسالة مرمّزة (quoted-printable أو base64)، والمكتبة تفكها هنا.
     */
    private static String partOf(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = partOf(multipart.getBodyPart(i), mimeType);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }
}
