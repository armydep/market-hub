package com.am.market_hub.alert.dto;

import java.math.BigDecimal;

import com.am.market_hub.alert.domain.AlertCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAlertRequest(
        @NotBlank String symbol,
        @NotNull AlertCondition condition,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal targetPrice) {
}
