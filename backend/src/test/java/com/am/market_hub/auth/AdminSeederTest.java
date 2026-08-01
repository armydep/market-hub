package com.am.market_hub.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.am.market_hub.user.domain.Role;
import com.am.market_hub.user.domain.User;
import com.am.market_hub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminSeederTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void seedsAnAdminWhenNoneExistsAndCredentialsAreConfigured() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("supersecret")).thenReturn("hashed");
        AdminSeeder seeder = new AdminSeeder(userRepository, passwordEncoder, "admin@example.com", "supersecret");

        seeder.run(null);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void doesNotSeedWhenAnAdminAlreadyExists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);
        AdminSeeder seeder = new AdminSeeder(userRepository, passwordEncoder, "admin@example.com", "supersecret");

        seeder.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNotSeedWhenCredentialsAreNotConfigured() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        AdminSeeder seeder = new AdminSeeder(userRepository, passwordEncoder, "", "");

        seeder.run(null);

        verify(userRepository, never()).save(any());
    }
}
