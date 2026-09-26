package com.chris64233.cc.sportsroster.api;

import java.time.LocalDate;

import com.chris64233.cc.sportsroster.domain.Season;

public record SeasonResponse(
        Long id,
        String code,
        String name,
        LocalDate registrationStart,
        LocalDate registrationEnd,
        int rosterSizeMax,
        int foreignPlayerMax,
        int homegrownMin,
        String domesticNationality) {

    public static SeasonResponse from(Season season) {
        return new SeasonResponse(season.getId(), season.getCode(), season.getName(),
                season.getRegistrationStart(), season.getRegistrationEnd(),
                season.getRosterSizeMax(), season.getForeignPlayerMax(),
                season.getHomegrownMin(), season.getDomesticNationality());
    }
}
