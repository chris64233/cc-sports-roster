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
@Table(name = "player", uniqueConstraints = @UniqueConstraint(name = "uk_player_code", columnNames = "code"))
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 32)
    private String nationality;

    @Column(name = "homegrown", nullable = false)
    private boolean homegrown;

    @Column(name = "suspension_until")
    private LocalDate suspensionUntil;

    protected Player() {
    }

    public Player(String code, String name, LocalDate birthDate, String nationality,
                  boolean homegrown, LocalDate suspensionUntil) {
        this.code = code;
        this.name = name;
        this.birthDate = birthDate;
        this.nationality = nationality;
        this.homegrown = homegrown;
        this.suspensionUntil = suspensionUntil;
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

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getNationality() {
        return nationality;
    }

    public boolean isHomegrown() {
        return homegrown;
    }

    public LocalDate getSuspensionUntil() {
        return suspensionUntil;
    }
}
