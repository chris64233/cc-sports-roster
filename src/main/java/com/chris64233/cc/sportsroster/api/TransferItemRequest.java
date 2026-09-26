package com.chris64233.cc.sportsroster.api;

import com.chris64233.cc.sportsroster.domain.TransferDirection;

import jakarta.validation.constraints.NotNull;

public record TransferItemRequest(
        @NotNull Long playerId,
        @NotNull TransferDirection direction) {
}
