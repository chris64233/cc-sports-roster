package com.chris64233.cc.sportsroster.api;

import com.chris64233.cc.sportsroster.domain.Team;

public record TeamResponse(Long id, String code, String name) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(team.getId(), team.getCode(), team.getName());
    }
}
