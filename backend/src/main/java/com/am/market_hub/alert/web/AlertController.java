package com.am.market_hub.alert.web;

import java.util.List;

import com.am.market_hub.alert.dto.AlertResponse;
import com.am.market_hub.alert.dto.CreateAlertRequest;
import com.am.market_hub.alert.dto.UpdateAlertRequest;
import com.am.market_hub.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-scoped price alerts (PRD F-006). Requires authentication (default-deny in SecurityConfig). */
@Tag(name = "Alerts", description = "One-time above/below price alerts (authentication required)")
@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Operation(summary = "List active alerts")
    @GetMapping
    public List<AlertResponse> listActive() {
        return alertService.listActive();
    }

    @Operation(summary = "List triggered, uncleared alerts")
    @GetMapping("/triggered")
    public List<AlertResponse> listTriggered() {
        return alertService.listTriggered();
    }

    @Operation(summary = "Create an alert",
            description = "400 for an unknown symbol or a condition already satisfied by the current price.")
    @PostMapping
    public ResponseEntity<AlertResponse> create(@Valid @RequestBody CreateAlertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.create(request));
    }

    @Operation(summary = "Update an active alert's condition/target price",
            description = "Symbol cannot be changed. 404 if not found, not yours, or not active.")
    @PatchMapping("/{id}")
    public AlertResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAlertRequest request) {
        return alertService.update(id, request);
    }

    @Operation(summary = "Delete an active alert", description = "404 if not found, not yours, or not active.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        alertService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Clear a triggered alert",
            description = "404 if not found, not yours, or not currently triggered-and-uncleared.")
    @PostMapping("/{id}/clear")
    public AlertResponse clear(@PathVariable Long id) {
        return alertService.clear(id);
    }
}
