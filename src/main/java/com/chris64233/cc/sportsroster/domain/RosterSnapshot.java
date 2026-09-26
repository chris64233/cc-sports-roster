package com.chris64233.cc.sportsroster.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 转会执行前后双方名单快照。失败时只生成 BEFORE 快照。
 * （申请，球队，阶段）唯一，保证重放执行不会重复留痕。
 */
@Entity
@Table(name = "roster_snapshot", uniqueConstraints = {
        @UniqueConstraint(name = "uk_snapshot_application_team_phase",
                columnNames = {"application_id", "team_id", "phase"})
})
public class RosterSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private TransferApplication application;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 16)
    private SnapshotPhase phase;

    @Column(name = "roster_version", nullable = false)
    private long rosterVersion;

    @Column(name = "size", nullable = false)
    private int size;

    @Column(name = "foreign_count", nullable = false)
    private int foreignCount;

    @Column(name = "homegrown_count", nullable = false)
    private int homegrownCount;

    @Lob
    @Column(name = "snapshot_json", nullable = false)
    private String snapshotJson;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    protected RosterSnapshot() {
    }

    public RosterSnapshot(TransferApplication application, Team team, SnapshotPhase phase,
                          long rosterVersion, int size, int foreignCount, int homegrownCount,
                          String snapshotJson, LocalDateTime capturedAt) {
        this.application = application;
        this.team = team;
        this.phase = phase;
        this.rosterVersion = rosterVersion;
        this.size = size;
        this.foreignCount = foreignCount;
        this.homegrownCount = homegrownCount;
        this.snapshotJson = snapshotJson;
        this.capturedAt = capturedAt;
    }

    public Long getId() {
        return id;
    }

    public TransferApplication getApplication() {
        return application;
    }

    public Team getTeam() {
        return team;
    }

    public SnapshotPhase getPhase() {
        return phase;
    }

    public long getRosterVersion() {
        return rosterVersion;
    }

    public int getSize() {
        return size;
    }

    public int getForeignCount() {
        return foreignCount;
    }

    public int getHomegrownCount() {
        return homegrownCount;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }
}
