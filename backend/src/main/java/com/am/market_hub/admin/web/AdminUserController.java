package com.am.market_hub.admin.web;

import com.am.market_hub.admin.dto.AdminUserPageResponse;
import com.am.market_hub.admin.dto.AdminUserResponse;
import com.am.market_hub.admin.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator-only user management (PRD F-010). Class-level
 * {@code @PreAuthorize} rather than per-method: every endpoint here requires
 * {@code ADMIN}, unlike {@code TestProtectedController}'s mix of gated and
 * ungated test endpoints.
 */
@Tag(name = "Admin", description = "Administrator-only user management")
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "List registered users", description = "Paginated, ordered by id. No search or sort.")
    @GetMapping
    public AdminUserPageResponse list(@RequestParam(required = false) Integer page) {
        return adminUserService.listUsers(page);
    }

    @Operation(summary = "Block a user account",
            description = "Idempotent: blocking an already-blocked account is a no-op.")
    @PostMapping("/{id}/block")
    public AdminUserResponse block(@PathVariable Long id) {
        return adminUserService.blockUser(id);
    }

    @Operation(summary = "Unblock a user account",
            description = "Idempotent: unblocking an already-unblocked account is a no-op.")
    @PostMapping("/{id}/unblock")
    public AdminUserResponse unblock(@PathVariable Long id) {
        return adminUserService.unblockUser(id);
    }
}
