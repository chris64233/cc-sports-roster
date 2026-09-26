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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 双方球队对一笔转会申请的决定事件。eventKey 幂等；
 * （申请，球队）唯一约束保证每支球队对一笔申请只能作出一次决定。
 */
@Entity
@Table(name = "transfer_decision_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_decision_event_key", columnNames = "event_key"),
        @UniqueConstraint(name = "uk_decision_application_team",
                columnNames = {"application_id", "team_id"})
})
public class TransferDecisionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;

    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private TransferApplication application;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16)
    private TransferDecision decision;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TransferDecisionEvent() {
    }

    public TransferDecisionEvent(String eventKey, TransferApplication application, Team team,
                                 TransferDecision decision, LocalDateTime createdAt) {
        this.eventKey = eventKey;
        this.application = application;
        this.team = team;
        this.decision = decision;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public TransferApplication getApplication() {
        return application;
    }

    public Team getTeam() {
        return team;
    }

    public TransferDecision getDecision() {
        return decision;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
