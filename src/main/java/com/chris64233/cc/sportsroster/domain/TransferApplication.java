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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * 转会（球员交换）申请。创建时冻结双方名单版本（fromRosterVersion/toRosterVersion），
 * 执行时若任一名单版本已变化则整笔失败。
 */
@Entity
@Table(name = "transfer_application", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transfer_application_no", columnNames = "application_no")
})
public class TransferApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_no", nullable = false, length = 64)
    private String applicationNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(optional = false)
    @JoinColumn(name = "from_team_id", nullable = false)
    private Team fromTeam;

    @ManyToOne(optional = false)
    @JoinColumn(name = "to_team_id", nullable = false)
    private Team toTeam;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransferStatus status;

    @Column(name = "from_roster_version", nullable = false)
    private long fromRosterVersion;

    @Column(name = "to_roster_version", nullable = false)
    private long toRosterVersion;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Lob
    @Column(name = "failure_details")
    private String failureDetails;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected TransferApplication() {
    }

    public TransferApplication(String applicationNo, Season season, Team fromTeam, Team toTeam,
                               LocalDate effectiveDate, TransferStatus status,
                               long fromRosterVersion, long toRosterVersion,
                               String contentHash, LocalDateTime createdAt) {
        this.applicationNo = applicationNo;
        this.season = season;
        this.fromTeam = fromTeam;
        this.toTeam = toTeam;
        this.effectiveDate = effectiveDate;
        this.status = status;
        this.fromRosterVersion = fromRosterVersion;
        this.toRosterVersion = toRosterVersion;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getApplicationNo() {
        return applicationNo;
    }

    public Season getSeason() {
        return season;
    }

    public Team getFromTeam() {
        return fromTeam;
    }

    public Team getToTeam() {
        return toTeam;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public long getFromRosterVersion() {
        return fromRosterVersion;
    }

    public long getToRosterVersion() {
        return toRosterVersion;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getFailureDetails() {
        return failureDetails;
    }

    public void setFailureDetails(String failureDetails) {
        this.failureDetails = failureDetails;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void markDecided(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void markExecuted(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public Long getVersion() {
        return version;
    }
}
