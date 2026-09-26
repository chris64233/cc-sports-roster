package com.chris64233.cc.sportsroster.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record ValidateRosterRequest(
        LocalDate submittedOn,
        @NotEmpty(message = "名单不能为空") List<@jakarta.validation.constraints.NotNull Long> playerIds) {
}
