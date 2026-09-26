package com.chris64233.cc.sportsroster.api;

import java.time.LocalDateTime;

public record RosterSnapshotResponse(
        String applicationNo,
        Long teamId,
        String teamCode,
        String phase,
        long rosterVersion,
        int size,
        int foreignCount,
        int homegrownCount,
        RosterResponse roster,
        LocalDateTime capturedAt) {
}
