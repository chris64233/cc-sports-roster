package com.chris64233.cc.sportsroster.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTransferRequest(
        @NotNull Long seasonId,
        @NotNull Long fromTeamId,
        @NotNull Long toTeamId,
        @NotNull LocalDate effectiveDate,
        @NotEmpty @Valid List<TransferItemRequest> items,
        @NotNull @Size(max = 64) String applicationNo) {
}
