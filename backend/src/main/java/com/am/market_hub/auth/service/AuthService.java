package com.am.market_hub.auth.service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import com.am.market_hub.auth.dto.AuthResponse;
import com.am.market_hub.auth.dto.LoginRequest;
import com.am.market_hub.auth.dto.RegisterRequest;
import com.am.market_hub.auth.security.JwtService;
import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.user.domain.User;
import com.am.market_hub.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final int maxFailedAttempts;
    private final Duration lockoutDuration;

    /**
     * A real BCrypt hash of an unguessable, never-used password, computed once.
     * login() checks against this when no account matches, so a nonexistent
     * email costs the same BCrypt work as a real one — without it, "no such
     * user" returns immediately while a wrong password still pays BCrypt's
     * ~50-100ms, and that timing gap alone lets an attacker enumerate
     * registered emails despite both cases returning an identical 401 body.
     */
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.auth.max-failed-attempts}") int maxFailedAttempts,
            @Value("${app.auth.lockout-duration-minutes}") double lockoutDurationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode("not-a-real-account-timing-guard");
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDuration = Duration.ofMillis((long) (lockoutDurationMinutes * 60_000));
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email).isPresent()) {
            throw ApiException.conflict("Email already registered");
        }
        User user = User.register(email, passwordEncoder.encode(request.password()));
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // The existence check above isn't atomic with the insert (READ
            // COMMITTED doesn't serialize two concurrent registrations for the
            // same email), so the unique constraint is the real guard; a
            // collision here means a duplicate slipped through the check, not
            // a genuinely unexpected failure.
            throw ApiException.conflict("Email already registered");
        }
        return toResponse(user);
    }

    /**
     * Writable (not read-only): a failed or successful attempt mutates the
     * user's lockout state. {@code user} stays managed for the duration of
     * this transaction, so those mutations are picked up by dirty checking —
     * no explicit save() call, unlike register()'s brand-new entity above.
     *
     * <p>{@code noRollbackFor = ApiException.class} is required, not
     * cosmetic: a wrong password mutates {@code failedLoginAttempts}/
     * {@code lockedUntil} and then throws {@code ApiException.unauthorized}
     * to report the failure to the caller. {@code ApiException} is
     * unchecked, so Spring's default rollback-on-unchecked-exception would
     * otherwise silently discard that exact mutation on every failed
     * attempt — the one case this method most needs to persist.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash); // timing guard, see field doc
            throw ApiException.unauthorized("Invalid email or password");
        }
        User user = maybeUser.get();

        if (user.isBlocked()) {
            throw ApiException.forbidden("Account is blocked");
        }
        if (user.isLockActive()) {
            throw ApiException.forbidden("Account temporarily locked, try again later");
        }
        if (user.hasExpiredLock()) {
            // Lazy expiry: an elapsed lock is a fresh start, not a still-guilty state.
            user.registerSuccessfulLogin();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(maxFailedAttempts, lockoutDuration);
            // Same generic message as the unknown-email case: never reveal which part was wrong.
            throw ApiException.unauthorized("Invalid email or password");
        }
        user.registerSuccessfulLogin();
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        String token = jwtService.issue(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getRole().name());
    }
}
