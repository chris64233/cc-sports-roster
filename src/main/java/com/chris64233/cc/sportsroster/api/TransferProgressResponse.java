package com.chris64233.cc.sportsroster.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TransferProgressResponse(
        String applicationNo,
        Long seasonId,
        String seasonCode,
        Long fromTeamId,
        String fromTeamCode,
        Long toTeamId,
        String toTeamCode,
        LocalDate effectiveDate,
        String status,
        long fromRosterVersion,
        long toRosterVersion,
        boolean fromTeamConfirmed,
        boolean toTeamConfirmed,
        List<TransferItemView> items,
        List<DecisionView> decisions,
        ViolationResponse failureViolations,
        LocalDateTime createdAt,
        LocalDateTime decidedAt,
        LocalDateTime executedAt,
        boolean replayed) {

    public record TransferItemView(Long playerId, String playerCode, String playerName, String direction) {
    }

    public record DecisionView(Long teamId, String teamCode, String decision,
                               String eventKey, LocalDateTime decidedAt) {
    }
}
