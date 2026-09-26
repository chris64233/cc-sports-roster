package com.chris64233.cc.sportsroster.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name) {
}
