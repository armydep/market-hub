package com.am.market_hub.auth.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only scaffolding. Nothing in S5 itself has a protected or role-gated
 * endpoint yet (the first real one arrives in S11), so
 * {@code AuthControllerIT} needs something concrete to exercise
 * "authenticated required" and "role hierarchy enforced" against. Picked up
 * automatically by component scanning (same base package as the app) in any
 * {@code @SpringBootTest} that boots the full context.
 */
@RestController
public class TestProtectedController {

    @GetMapping("/test/protected")
    public String protectedEndpoint() {
        return "ok";
    }

    @GetMapping("/test/admin-only")
    public String adminOnlyEndpoint() {
        return "ok";
    }
}
