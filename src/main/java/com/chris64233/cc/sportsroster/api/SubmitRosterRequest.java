package com.chris64233.cc.sportsroster.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SubmitRosterRequest(
        @NotNull Long expectedVersion,
        LocalDate submittedOn,
        @NotEmpty List<@NotNull Long> playerIds) {
}
