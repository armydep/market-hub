package com.am.market_hub.user.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record UpdatePreferencesRequest(@NotEmpty List<String> visibleColumns) {
}
