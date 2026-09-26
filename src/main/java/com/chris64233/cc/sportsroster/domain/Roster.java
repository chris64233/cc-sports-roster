package com.chris64233.cc.sportsroster.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "roster", uniqueConstraints = @UniqueConstraint(name = "uk_roster_team_season",
        columnNames = {"team_id", "season_id"}))
public class Roster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    protected Roster() {
    }

    public Roster(Team team, Season season, LocalDateTime submittedAt) {
        this.team = team;
        this.season = season;
        this.submittedAt = submittedAt;
    }

    public Long getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public Season getSeason() {
        return season;
    }

    public long getVersion() {
        return version;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}
