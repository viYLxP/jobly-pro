package com.jobly.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jobly.admin.AdminDtos.AdminEmail;
import com.jobly.admin.AdminDtos.AdminUser;
import com.jobly.admin.AdminDtos.Stats;
import com.jobly.common.CurrentUser;
import com.jobly.common.PageResponse;
import com.jobly.mail.OutboxStatus;
import com.jobly.user.Role;
import com.jobly.user.UserStatus;

/**
 * The admin area. Only ADMIN accounts reach it (SecurityConfig).
 * منطقة الأدمن. حسابات ADMIN فقط توصل لها (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/users")
    PageResponse<AdminUser> users(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) Role role,
                                  @RequestParam(required = false) UserStatus status,
                                  @PageableDefault(size = 20) Pageable pageable) {
        String search = q != null && q.length() > 100 ? q.substring(0, 100) : q;
        return PageResponse.of(service.users(search, role, status, pageable), java.util.function.Function.identity());
    }

    @PostMapping("/users/{id}/suspend")
    AdminUser suspend(@PathVariable long id) {
        return service.suspend(CurrentUser.id(), id);
    }

    @PostMapping("/users/{id}/restore")
    AdminUser restore(@PathVariable long id) {
        return service.restore(id);
    }

    @GetMapping("/stats")
    Stats stats() {
        return service.stats();
    }

    @GetMapping("/emails")
    PageResponse<AdminEmail> emails(@RequestParam(required = false) OutboxStatus status,
                                    @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.emails(status, pageable), java.util.function.Function.identity());
    }

    @PostMapping("/emails/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retry(@PathVariable long id) {
        service.retryEmail(id);
    }
}
