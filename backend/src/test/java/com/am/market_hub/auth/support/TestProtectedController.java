package com.am.market_hub.auth.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only scaffolding. Nothing in S5 itself has a plain protected endpoint
 * yet, so {@code AuthControllerIT} needs something concrete to exercise
 * "authentication required" against. Picked up automatically by component
 * scanning (same base package as the app) in any {@code @SpringBootTest} that
 * boots the full context.
 *
 * <p>The former {@code /test/admin-only} role-gated sibling was retired in
 * S11 now that a real admin-only endpoint exists ({@code GET /admin/users});
 * this one stays until a later slice adds a real authenticated-but-not-admin
 * endpoint (e.g. S8's account endpoints).
 */
@RestController
public class TestProtectedController {

    @GetMapping("/test/protected")
    public String protectedEndpoint() {
        return "ok";
    }
}
