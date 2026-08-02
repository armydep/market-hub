package com.am.market_hub.auth.service;

import java.util.Locale;
import java.util.Optional;

import com.am.market_hub.auth.dto.AuthResponse;
import com.am.market_hub.auth.dto.LoginRequest;
import com.am.market_hub.auth.dto.RegisterRequest;
import com.am.market_hub.auth.security.JwtService;
import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.user.domain.User;
import com.am.market_hub.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * A real BCrypt hash of an unguessable, never-used password, computed once.
     * login() checks against this when no account matches, so a nonexistent
     * email costs the same BCrypt work as a real one — without it, "no such
     * user" returns immediately while a wrong password still pays BCrypt's
     * ~50-100ms, and that timing gap alone lets an attacker enumerate
     * registered emails despite both cases returning an identical 401 body.
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode("not-a-real-account-timing-guard");
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

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        Optional<User> user = userRepository.findByEmail(email);
        String hashToCheck = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);
        // Same generic message either way: never reveal which part was wrong.
        if (user.isEmpty() || !passwordMatches) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return toResponse(user.get());
    }

    private AuthResponse toResponse(User user) {
        String token = jwtService.issue(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getRole().name());
    }
}
