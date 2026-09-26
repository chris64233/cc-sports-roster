package com.chris64233.cc.sportsroster.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlayerRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull LocalDate birthDate,
        @NotBlank @Size(max = 32) String nationality,
        boolean homegrown,
        LocalDate suspensionUntil) {
}
