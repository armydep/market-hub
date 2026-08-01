package com.am.market_hub.auth.service;

import com.am.market_hub.auth.dto.AuthResponse;
import com.am.market_hub.auth.dto.LoginRequest;
import com.am.market_hub.auth.dto.RegisterRequest;
import com.am.market_hub.auth.security.JwtService;
import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.user.domain.User;
import com.am.market_hub.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.conflict("Email already registered");
        }
        User user = "ADMIN".equals(request.role())
                ? User.seedAdmin(email, passwordEncoder.encode(request.password()))
                : User.register(email, passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        // Same generic message on a wrong password: never reveal which part was wrong.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        String token = jwtService.issue(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getRole().name());
    }
}
