package com.chris64233.cc.sportsroster.api;

public record RegistrationResponse(
        Long seasonId,
        String seasonCode,
        Long playerId,
        String playerCode,
        boolean registered,
        Long teamId,
        String teamCode,
        Long rosterVersion) {
}
