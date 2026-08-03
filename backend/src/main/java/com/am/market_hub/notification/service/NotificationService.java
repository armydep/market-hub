package com.am.market_hub.notification.service;

import java.util.List;

import com.am.market_hub.auth.security.CurrentUser;
import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.notification.domain.Notification;
import com.am.market_hub.notification.dto.NotificationResponse;
import com.am.market_hub.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUser currentUser;

    public NotificationService(NotificationRepository notificationRepository, CurrentUser currentUser) {
        this.notificationRepository = notificationRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list() {
        return notificationRepository
                .findByUserIdAndClearedAtIsNullOrderByTriggeredAtDesc(currentUser.userId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /** 404 if not found, not the caller's own, or already cleared — no distinguishing signal. */
    @Transactional
    public NotificationResponse clear(Long id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, currentUser.userId())
                .filter(n -> n.getClearedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Unknown notification: " + id));
        notification.clear();
        return NotificationResponse.from(notification);
    }
}
