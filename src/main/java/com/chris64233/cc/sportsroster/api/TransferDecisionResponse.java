package com.chris64233.cc.sportsroster.api;

import java.time.LocalDateTime;

public record TransferDecisionResponse(
        String applicationNo,
        Long teamId,
        String teamCode,
        String decision,
        String eventKey,
        String applicationStatus,
        LocalDateTime decidedAt,
        boolean replayed) {
}
