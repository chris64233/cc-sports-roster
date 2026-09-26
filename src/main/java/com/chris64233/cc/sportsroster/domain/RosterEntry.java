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

@Entity
@Table(name = "roster_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_entry_roster_player", columnNames = {"roster_id", "player_id"}),
        @UniqueConstraint(name = "uk_entry_season_player", columnNames = {"season_id", "player_id"})
})
public class RosterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "roster_id", nullable = false)
    private Roster roster;

    @ManyToOne(optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    protected RosterEntry() {
    }

    public RosterEntry(Roster roster, Season season, Player player, LocalDateTime registeredAt) {
        this.roster = roster;
        this.season = season;
        this.player = player;
        this.registeredAt = registeredAt;
    }

    /**
     * 转会执行时把本条赛季注册迁移到另一份球队名单，球员的赛季唯一注册关系不变。
     */
    public void moveTo(Roster targetRoster) {
        this.roster = targetRoster;
    }

    public Long getId() {
        return id;
    }

    public Roster getRoster() {
        return roster;
    }

    public Season getSeason() {
        return season;
    }

    public Player getPlayer() {
        return player;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
}
