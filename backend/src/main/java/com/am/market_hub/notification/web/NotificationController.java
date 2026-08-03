package com.am.market_hub.notification.web;

import java.util.List;

import com.am.market_hub.notification.dto.NotificationResponse;
import com.am.market_hub.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-scoped alert-trigger notifications (PRD F-007). Requires authentication (default-deny in SecurityConfig). */
@Tag(name = "Notifications", description = "In-app alert-trigger notifications (authentication required)")
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "List visible (uncleared) notifications, most-recently-triggered first")
    @GetMapping
    public List<NotificationResponse> list() {
        return notificationService.list();
    }

    @Operation(summary = "Clear a notification", description = "404 if not found, not yours, or already cleared.")
    @PostMapping("/{id}/clear")
    public NotificationResponse clear(@PathVariable Long id) {
        return notificationService.clear(id);
    }
}
