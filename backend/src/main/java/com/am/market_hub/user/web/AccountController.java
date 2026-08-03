package com.am.market_hub.user.web;

import com.am.market_hub.user.dto.AccountResponse;
import com.am.market_hub.user.dto.ChangePasswordRequest;
import com.am.market_hub.user.dto.PreferencesResponse;
import com.am.market_hub.user.dto.UpdateAccountRequest;
import com.am.market_hub.user.dto.UpdatePreferencesRequest;
import com.am.market_hub.user.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own account (PRD F-009). Requires authentication (default-deny in SecurityConfig). */
@Tag(name = "Account", description = "View/update the caller's own account and preferences (authentication required)")
@RestController
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "View the caller's own account")
    @GetMapping
    public AccountResponse getAccount() {
        return accountService.getAccount();
    }

    @Operation(summary = "Change the caller's email",
            description = "Requires the current password. 409 if the new email is already registered.")
    @PatchMapping
    public AccountResponse updateAccount(@Valid @RequestBody UpdateAccountRequest request) {
        return accountService.updateAccount(request);
    }

    @Operation(summary = "Change the caller's password", description = "Requires the current password.")
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "View the caller's visible-column preferences",
            description = "Returns the application default set if the caller has never saved one.")
    @GetMapping("/preferences")
    public PreferencesResponse getPreferences() {
        return accountService.getPreferences();
    }

    @Operation(summary = "Replace the caller's visible-column preferences",
            description = "400 for any column key outside the supported catalog.")
    @PutMapping("/preferences")
    public PreferencesResponse updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        return accountService.updatePreferences(request);
    }
}
