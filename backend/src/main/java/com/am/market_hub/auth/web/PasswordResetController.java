package com.am.market_hub.auth.web;

import com.am.market_hub.auth.dto.PasswordResetConfirmRequest;
import com.am.market_hub.auth.dto.PasswordResetRequest;
import com.am.market_hub.auth.dto.PasswordResetResponse;
import com.am.market_hub.auth.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Password reset (no auth required)")
@RestController
@RequestMapping("/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Operation(summary = "Request a password reset",
            description = "Always responds identically whether or not the email belongs to a registered account.")
    @PostMapping("/request")
    public PasswordResetResponse request(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return new PasswordResetResponse("If an account with that email exists, a password reset link has been sent.");
    }

    @Operation(summary = "Confirm a password reset",
            description = "400 if the token is unknown, expired, or already used.")
    @PostMapping("/confirm")
    public PasswordResetResponse confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return new PasswordResetResponse("Your password has been updated. You can now sign in.");
    }
}
