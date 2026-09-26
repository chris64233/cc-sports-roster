package com.chris64233.cc.sportsroster.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SeasonRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull LocalDate registrationStart,
        @NotNull LocalDate registrationEnd,
        @Min(1) int rosterSizeMax,
        @Min(0) int foreignPlayerMax,
        @Min(0) int homegrownMin,
        @NotBlank @Size(max = 32) String domesticNationality) {
}
