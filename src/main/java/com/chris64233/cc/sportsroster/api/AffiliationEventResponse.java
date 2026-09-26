package com.chris64233.cc.sportsroster.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AffiliationEventResponse(
        Long playerId,
        String playerCode,
        Long seasonId,
        String seasonCode,
        String eventType,
        Long teamId,
        String teamCode,
        String applicationNo,
        LocalDate effectiveDate,
        LocalDateTime occurredAt) {
}
