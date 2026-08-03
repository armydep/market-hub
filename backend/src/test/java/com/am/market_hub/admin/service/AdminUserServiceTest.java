package com.am.market_hub.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.am.market_hub.admin.domain.AdminAuditLog;
import com.am.market_hub.admin.repository.AdminAuditLogRepository;
import com.am.market_hub.auth.security.CurrentUser;
import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.user.domain.User;
import com.am.market_hub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

class AdminUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminAuditLogRepository auditLogRepository = mock(AdminAuditLogRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final AdminUserService service = new AdminUserService(userRepository, auditLogRepository, currentUser);

    @Test
    void blockingAnUnblockedUserBlocksItAndWritesOneAuditRow() {
        User target = User.register("trader@example.com", "hash");
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(currentUser.userId()).thenReturn(99L);

        service.blockUser(1L);

        assertThat(target.isBlocked()).isTrue();
        verify(auditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void blockingAnAlreadyBlockedUserIsANoOpAndWritesNoAuditRow() {
        User target = User.register("trader@example.com", "hash");
        target.block();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));

        service.blockUser(1L);

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void unblockingABlockedUserUnblocksItAndWritesOneAuditRow() {
        User target = User.register("trader@example.com", "hash");
        target.block();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(currentUser.userId()).thenReturn(99L);

        service.unblockUser(1L);

        assertThat(target.isBlocked()).isFalse();
        verify(auditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void unblockingAnAlreadyUnblockedUserIsANoOpAndWritesNoAuditRow() {
        User target = User.register("trader@example.com", "hash");
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));

        service.unblockUser(1L);

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void blockingAnUnknownUserThrowsNotFound() {
        when(userRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.blockUser(404L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(404));
    }
}
