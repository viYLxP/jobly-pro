package com.jobly.alert;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobly.alert.AlertDtos.UnsubscribeRequest;
import com.jobly.alert.AlertDtos.UnsubscribeResponse;

/**
 * The "stop my alerts" link from alert emails. Public, because the person clicking is not signed in.
 * رابط "إيقاف تنبيهاتي" من إيميلات التنبيه. عام، لأن الشخص اللي يضغط ما هو مسجّل دخول.
 *
 * <p>It is a POST, not a GET: some mail programs open every link in a message to scan it, and that must not
 * switch alerts off by itself. The web page asks the person to press a button first.</p>
 * <p>هو POST وليس GET: بعض برامج البريد تفتح كل رابط في الرسالة لفحصه، وهذا ما لازم يوقف التنبيهات من نفسه.
 * وصفحة الويب تطلب من الشخص يضغط زر أولًا.</p>
 */
@RestController
@RequestMapping("/api/public")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @PostMapping("/unsubscribe")
    UnsubscribeResponse unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        return new UnsubscribeResponse("UNSUBSCRIBED", service.unsubscribe(request.token()));
    }
}
