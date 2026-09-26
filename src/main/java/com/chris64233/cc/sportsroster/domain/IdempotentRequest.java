package com.chris64233.cc.sportsroster.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotent_request")
public class IdempotentRequest {

    @Id
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "season_id", nullable = false)
    private Long seasonId;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Lob
    @Column(name = "response_json", nullable = false)
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected IdempotentRequest() {
    }

    public IdempotentRequest(String idempotencyKey, Long teamId, Long seasonId, String contentHash,
                             int statusCode, String responseJson, LocalDateTime createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.teamId = teamId;
        this.seasonId = seasonId;
        this.contentHash = contentHash;
        this.statusCode = statusCode;
        this.responseJson = responseJson;
        this.createdAt = createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Long getSeasonId() {
        return seasonId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
