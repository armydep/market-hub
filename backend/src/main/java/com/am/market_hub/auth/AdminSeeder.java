package com.am.market_hub.auth;

import com.am.market_hub.user.domain.Role;
import com.am.market_hub.user.domain.User;
import com.am.market_hub.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the one admin account this app supports self-creating. Runs on
 * every startup but only acts when no {@code ADMIN} exists yet, so it's safe
 * to run repeatedly; this is the *only* way to become an admin in Phase 1 —
 * registration always mints TRADER, and there is no role-change endpoint.
 *
 * <p>Degrades gracefully when the env vars aren't configured (e.g. in tests),
 * mirroring the existing "boot with no CMC_API_KEY" pattern rather than
 * failing startup.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("No admin account exists and ADMIN_EMAIL/ADMIN_PASSWORD are not configured; skipping seed.");
            return;
        }
        userRepository.save(User.seedAdmin(adminEmail.toLowerCase(), passwordEncoder.encode(adminPassword)));
        log.info("Seeded admin account for {}", adminEmail);
    }
}
