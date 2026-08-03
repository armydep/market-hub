package com.am.market_hub.admin.service;

import com.am.market_hub.admin.domain.AdminAction;
import com.am.market_hub.admin.domain.AdminAuditLog;
import com.am.market_hub.admin.dto.AdminUserPageResponse;
import com.am.market_hub.admin.dto.AdminUserResponse;
import com.am.market_hub.admin.repository.AdminAuditLogRepository;
import com.am.market_hub.auth.security.CurrentUser;
import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.user.domain.User;
import com.am.market_hub.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    /**
     * Fixed, not configurable: F-010 is intentionally basic and has no
     * page-size-selection requirement (unlike the market dashboard).
     */
    private static final int PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final CurrentUser currentUser;

    public AdminUserService(UserRepository userRepository, AdminAuditLogRepository auditLogRepository,
            CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public AdminUserPageResponse listUsers(Integer page) {
        return AdminUserPageResponse.from(
                userRepository.findAll(PageRequest.of(resolvePage(page), PAGE_SIZE, Sort.by("id").ascending())));
    }

    /**
     * Idempotent: blocking an already-blocked user is a no-op that neither
     * changes state nor writes an audit row — an audit entry exists only when
     * something actually changed (spec's resolved open question 1).
     */
    @Transactional
    public AdminUserResponse blockUser(Long targetId) {
        User target = findTarget(targetId);
        if (!target.isBlocked()) {
            target.block();
            auditLogRepository.save(AdminAuditLog.of(currentUser.userId(), AdminAction.BLOCK_USER, targetId));
        }
        return AdminUserResponse.from(target);
    }

    @Transactional
    public AdminUserResponse unblockUser(Long targetId) {
        User target = findTarget(targetId);
        if (target.isBlocked()) {
            target.unblock();
            auditLogRepository.save(AdminAuditLog.of(currentUser.userId(), AdminAction.UNBLOCK_USER, targetId));
        }
        return AdminUserResponse.from(target);
    }

    private User findTarget(Long id) {
        return userRepository.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Unknown user: " + id));
    }

    private int resolvePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw ApiException.badRequest("Page must not be negative: " + page);
        }
        return page;
    }
}
