package com.am.market_hub.user.service;

import java.util.List;
import java.util.Locale;

import com.am.market_hub.auth.security.CurrentUser;
import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.market.domain.CoinColumn;
import com.am.market_hub.user.domain.User;
import com.am.market_hub.user.domain.UserPreference;
import com.am.market_hub.user.dto.AccountResponse;
import com.am.market_hub.user.dto.ChangePasswordRequest;
import com.am.market_hub.user.dto.PreferencesResponse;
import com.am.market_hub.user.dto.UpdateAccountRequest;
import com.am.market_hub.user.dto.UpdatePreferencesRequest;
import com.am.market_hub.user.repository.UserPreferenceRepository;
import com.am.market_hub.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AccountService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;
    private final List<String> defaultVisibleColumns;

    public AccountService(
            UserRepository userRepository,
            UserPreferenceRepository preferenceRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            CurrentUser currentUser,
            @Value("${app.market.default-visible-columns}") List<String> defaultVisibleColumns) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
        this.defaultVisibleColumns = List.copyOf(defaultVisibleColumns);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount() {
        return AccountResponse.from(currentUserEntity());
    }

    /**
     * The current password is required for both this and {@link #changePassword},
     * per docs/slices/08-account-management.md's resolved decision: a bearer
     * token alone must not be enough to redirect the account's password-reset
     * recovery identity.
     */
    @Transactional
    public AccountResponse updateAccount(UpdateAccountRequest request) {
        User user = currentUserEntity();
        requireCurrentPassword(user, request.currentPassword());

        String newEmail = request.email().toLowerCase(Locale.ROOT);
        if (!newEmail.equals(user.getEmail()) && userRepository.findByEmail(newEmail).isPresent()) {
            throw ApiException.conflict("Email already registered");
        }
        // A concurrent race past this check surfaces at transaction commit (this
        // is a managed-entity update, not a fresh IDENTITY insert, so there's no
        // local flush to catch it against) and is handled by
        // GlobalExceptionHandler's DataIntegrityViolationException mapping.
        user.changeEmail(newEmail);
        return AccountResponse.from(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUserEntity();
        requireCurrentPassword(user, request.currentPassword());
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional(readOnly = true)
    public PreferencesResponse getPreferences() {
        return preferenceRepository.findById(currentUser.userId())
                .map(pref -> new PreferencesResponse(objectMapper.readValue(pref.getVisibleColumnsJson(), STRING_LIST)))
                .orElseGet(() -> new PreferencesResponse(defaultVisibleColumns));
    }

    @Transactional
    public PreferencesResponse updatePreferences(UpdatePreferencesRequest request) {
        List<String> unknown = request.visibleColumns().stream()
                .filter(key -> CoinColumn.byKey(key).isEmpty())
                .toList();
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("Unknown column key(s): " + unknown + "; supported: " + CoinColumn.keys());
        }

        String json = objectMapper.writeValueAsString(request.visibleColumns());
        Long userId = currentUser.userId();
        preferenceRepository.findById(userId).ifPresentOrElse(
                pref -> pref.updateVisibleColumns(json),
                () -> preferenceRepository.save(UserPreference.of(userId, json)));
        return new PreferencesResponse(request.visibleColumns());
    }

    private User currentUserEntity() {
        // The token is only ever issued for a real, currently-existing user id
        // (see JwtAuthFilter), so an empty result here would mean the account
        // was deleted mid-session — not a case Phase 1's data model allows.
        return userRepository.findById(currentUser.userId())
                .orElseThrow(() -> ApiException.notFound("Unknown account"));
    }

    private void requireCurrentPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
    }
}
