package com.chris64233.cc.sportsroster.api;

import com.chris64233.cc.sportsroster.domain.TransferDecision;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferDecisionRequest(
        @NotNull Long teamId,
        @NotNull TransferDecision decision,
        @NotNull @Size(max = 64) String eventKey) {
}
