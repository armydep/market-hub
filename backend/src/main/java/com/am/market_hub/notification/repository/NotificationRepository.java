package com.am.market_hub.notification.repository;

import java.util.List;
import java.util.Optional;

import com.am.market_hub.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdAndClearedAtIsNullOrderByTriggeredAtDesc(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
