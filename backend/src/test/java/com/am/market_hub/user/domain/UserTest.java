package com.am.market_hub.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class UserTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    @Test
    void doesNotLockBeforeTheThresholdIsReached() {
        User user = User.register("trader@example.com", "hash");

        user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT);
        user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLockActive()).isFalse();
    }

    @Test
    void locksExactlyAtTheThreshold() {
        User user = User.register("trader@example.com", "hash");

        user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT);
        user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT);
        user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.isLockActive()).isTrue();
        assertThat(user.hasExpiredLock()).isFalse();
    }

    @Test
    void aFutureLockedUntilIsActiveAndNotExpired() {
        User user = User.register("trader@example.com", "hash");
        user.registerFailedLogin(1, Duration.ofMinutes(1));

        assertThat(user.isLockActive()).isTrue();
        assertThat(user.hasExpiredLock()).isFalse();
    }

    @Test
    void aPastLockedUntilIsExpiredAndNotActive() {
        User user = User.register("trader@example.com", "hash");
        user.registerFailedLogin(1, Duration.ofMillis(-1));

        assertThat(user.isLockActive()).isFalse();
        assertThat(user.hasExpiredLock()).isTrue();
    }

    @Test
    void noLockAtAllIsNeitherActiveNorExpired() {
        User user = User.register("trader@example.com", "hash");

        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLockActive()).isFalse();
        assertThat(user.hasExpiredLock()).isFalse();
    }

    @Test
    void successfulLoginClearsBothFields() {
        User user = User.register("trader@example.com", "hash");
        user.registerFailedLogin(1, Duration.ofMinutes(15));

        user.registerSuccessfulLogin();

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }
}
