package com.chris64233.cc.sportsroster.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RosterPlayerResponse(
        Long id,
        String code,
        String name,
        LocalDate birthDate,
        String nationality,
        boolean homegrown,
        LocalDateTime registeredAt) {
}
