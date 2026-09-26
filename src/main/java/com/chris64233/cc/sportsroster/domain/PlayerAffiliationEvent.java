package com.chris64233.cc.sportsroster.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 球员赛季归属时间线事件：注册（含转会前历史回填）、转入、转出。
 */
@Entity
@Table(name = "player_affiliation_event", indexes = {
        @Index(name = "idx_affiliation_season_player", columnList = "season_id,player_id"),
        @Index(name = "idx_affiliation_player", columnList = "player_id")
})
public class PlayerAffiliationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    private AffiliationEventType eventType;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private TransferApplication application;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected PlayerAffiliationEvent() {
    }

    public PlayerAffiliationEvent(Season season, Player player, Team team,
                                  AffiliationEventType eventType, TransferApplication application,
                                  LocalDate effectiveDate, LocalDateTime occurredAt) {
        this.season = season;
        this.player = player;
        this.team = team;
        this.eventType = eventType;
        this.application = application;
        this.effectiveDate = effectiveDate;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public Season getSeason() {
        return season;
    }

    public Player getPlayer() {
        return player;
    }

    public Team getTeam() {
        return team;
    }

    public AffiliationEventType getEventType() {
        return eventType;
    }

    public TransferApplication getApplication() {
        return application;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
