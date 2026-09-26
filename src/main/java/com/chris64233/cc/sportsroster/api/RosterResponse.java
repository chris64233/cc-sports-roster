package com.chris64233.cc.sportsroster.api;

import java.time.LocalDateTime;
import java.util.List;

public record RosterResponse(
        Long teamId,
        String teamCode,
        Long seasonId,
        String seasonCode,
        long version,
        LocalDateTime submittedAt,
        int size,
        int foreignCount,
        int homegrownCount,
        List<RosterPlayerResponse> players) {
}
