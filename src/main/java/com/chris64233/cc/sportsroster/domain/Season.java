package com.chris64233.cc.sportsroster.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "season", uniqueConstraints = @UniqueConstraint(name = "uk_season_code", columnNames = "code"))
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "registration_start", nullable = false)
    private LocalDate registrationStart;

    @Column(name = "registration_end", nullable = false)
    private LocalDate registrationEnd;

    @Column(name = "roster_size_max", nullable = false)
    private int rosterSizeMax;

    @Column(name = "foreign_player_max", nullable = false)
    private int foreignPlayerMax;

    @Column(name = "homegrown_min", nullable = false)
    private int homegrownMin;

    @Column(name = "domestic_nationality", nullable = false, length = 32)
    private String domesticNationality;

    protected Season() {
    }

    public Season(String code, String name, LocalDate registrationStart, LocalDate registrationEnd,
                  int rosterSizeMax, int foreignPlayerMax, int homegrownMin, String domesticNationality) {
        this.code = code;
        this.name = name;
        this.registrationStart = registrationStart;
        this.registrationEnd = registrationEnd;
        this.rosterSizeMax = rosterSizeMax;
        this.foreignPlayerMax = foreignPlayerMax;
        this.homegrownMin = homegrownMin;
        this.domesticNationality = domesticNationality;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public LocalDate getRegistrationStart() {
        return registrationStart;
    }

    public LocalDate getRegistrationEnd() {
        return registrationEnd;
    }

    public int getRosterSizeMax() {
        return rosterSizeMax;
    }

    public int getForeignPlayerMax() {
        return foreignPlayerMax;
    }

    public int getHomegrownMin() {
        return homegrownMin;
    }

    public String getDomesticNationality() {
        return domesticNationality;
    }
}
