package com.chris64233.cc.sportsroster.api;

import com.chris64233.cc.sportsroster.domain.Player;

import java.time.LocalDate;

public record PlayerResponse(
        Long id,
        String code,
        String name,
        LocalDate birthDate,
        String nationality,
        boolean homegrown,
        LocalDate suspensionUntil) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getCode(), player.getName(),
                player.getBirthDate(), player.getNationality(), player.isHomegrown(),
                player.getSuspensionUntil());
    }
}
