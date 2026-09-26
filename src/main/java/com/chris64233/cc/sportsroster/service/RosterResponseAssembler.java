package com.chris64233.cc.sportsroster.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.chris64233.cc.sportsroster.api.RosterPlayerResponse;
import com.chris64233.cc.sportsroster.api.RosterResponse;
import com.chris64233.cc.sportsroster.domain.Roster;
import com.chris64233.cc.sportsroster.domain.RosterEntry;

@Component
public class RosterResponseAssembler {

    public RosterResponse assemble(Roster roster, List<RosterEntry> entries) {
        String domesticNationality = roster.getSeason().getDomesticNationality();
        List<RosterPlayerResponse> players = entries.stream()
                .map(entry -> new RosterPlayerResponse(
                        entry.getPlayer().getId(),
                        entry.getPlayer().getCode(),
                        entry.getPlayer().getName(),
                        entry.getBirthDate(),
                        entry.getPlayer().getNationality(),
                        entry.getPlayer().isHomegrown(),
                        entry.getRegisteredAt()))
                .toList();
        int foreignCount = (int) players.stream()
                .filter(player -> !domesticNationality.equalsIgnoreCase(player.nationality()))
                .count();
        int homegrownCount = (int) players.stream().filter(RosterPlayerResponse::homegrown).count();
        return new RosterResponse(
                roster.getTeam().getId(),
                roster.getTeam().getCode(),
                roster.getSeason().getId(),
                roster.getSeason().getCode(),
                roster.getVersion(),
                roster.getSubmittedAt(),
                players.size(),
                foreignCount,
                homegrownCount,
                players);
    }
}
