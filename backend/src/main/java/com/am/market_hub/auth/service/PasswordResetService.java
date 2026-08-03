package com.am.market_hub.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import com.am.market_hub.auth.domain.PasswordResetToken;
import com.am.market_hub.auth.email.EmailSender;
import com.am.market_hub.auth.repository.PasswordResetTokenRepository;
import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.user.domain.User;
import com.am.market_hub.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordResetService {

    /**
     * Not a real user id (ids are sequential positives) — used only so the
     * no-match path in {@link #prepareReset} runs the same query shape as
     * the match path, for timing symmetry.
     */
    private static final long NO_MATCH_QUERY_ID = -1L;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final TransactionTemplate transactionTemplate;
    private final Duration tokenLifetime;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            PlatformTransactionManager transactionManager,
            @Value("${app.auth.password-reset-token-lifetime-minutes}") double tokenLifetimeMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // double, not long: tests override this to a sub-minute fraction for
        // a fast expiry check, same pattern as app.auth.lockout-duration-minutes.
        this.tokenLifetime = Duration.ofMillis((long) (tokenLifetimeMinutes * 60_000));
    }

    /**
     * The DB work runs in its own transaction (via {@code transactionTemplate},
     * not a method-level {@code @Transactional}); the {@link EmailSender} call
     * happens only after that transaction has committed, so a slow or hung
     * send can never hold a DB connection open — the same discipline
     * {@code CryptoPoller} already applies to its provider call
     * (constraints.md: "the poll transaction never spans the provider call").
     */
    public void requestReset(String email) {
        String normalized = email.toLowerCase(Locale.ROOT);
        PendingReset pending = transactionTemplate.execute(status -> prepareReset(normalized));
        if (pending != null) {
            emailSender.sendPasswordResetEmail(pending.email(), pending.rawToken());
        }
        // No else branch: identical response either way is the controller's
        // job, not this method's — no branch here reveals whether the email
        // matched an account.
    }

    /**
     * Does the same shape of work regardless of whether the email matched,
     * so response timing doesn't reveal it either — mirrors AuthService's
     * dummy-hash guard for the analogous login case. Returns null (no email
     * sent) when there's no match.
     */
    private PendingReset prepareReset(String normalizedEmail) {
        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);
        long queryId = maybeUser.map(User::getId).orElse(NO_MATCH_QUERY_ID);
        // Only the newest requested token is ever valid (spec's resolved open question 2).
        tokenRepository.findByUserIdAndUsedAtIsNull(queryId).forEach(PasswordResetToken::markUsed);
        String rawToken = generateToken();
        String tokenHash = hash(rawToken);
        if (maybeUser.isEmpty()) {
            return null;
        }
        User user = maybeUser.get();
        tokenRepository.save(PasswordResetToken.issue(user.getId(), tokenHash, tokenLifetime));
        return new PendingReset(user.getEmail(), rawToken);
    }

    private record PendingReset(String email, String rawToken) {
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .filter(PasswordResetToken::isValid)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired token"));
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired token"));
        user.changePassword(passwordEncoder.encode(newPassword));
        // A successful reset is at least as strong a proof of ownership as a
        // correct password, so it gets the same recovery effect S6 already
        // gives a successful login.
        //
        // Known Phase-1 limitation, not fixed here: this does not invalidate
        // any JWT already issued before the reset. Authorization is
        // otherwise stateless (only `blocked` is rechecked per request, per
        // domain-model.md), so a session token issued before a reset stays
        // valid until its normal expiry. Closing this would need a
        // passwordChangedAt claim check in JwtAuthFilter plus a new
        // migration — a large enough change to warrant its own decision
        // rather than a quiet addition here.
        user.registerSuccessfulLogin();
        token.markUsed();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * A fast hash, not a slow salted KDF: the token is already high-entropy
     * random (unlike a human-chosen password), so there's nothing for a
     * BCrypt-style hash to defend against here that SHA-256 doesn't already
     * cover against a stolen database read — and a fast hash is what lets
     * confirm() find the token with one indexed lookup.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
