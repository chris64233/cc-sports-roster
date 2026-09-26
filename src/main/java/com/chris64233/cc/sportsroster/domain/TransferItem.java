package com.chris64233.cc.sportsroster.domain;

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
 * 一笔转会申请中的单个球员及其交换方向。
 */
@Entity
@Table(name = "transfer_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transfer_item_player",
                columnNames = {"application_id", "player_id"})
})
public class TransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private TransferApplication application;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private TransferDirection direction;

    protected TransferItem() {
    }

    public TransferItem(TransferApplication application, Player player, TransferDirection direction) {
        this.application = application;
        this.player = player;
        this.direction = direction;
    }

    public Long getId() {
        return id;
    }

    public TransferApplication getApplication() {
        return application;
    }

    public Player getPlayer() {
        return player;
    }

    public TransferDirection getDirection() {
        return direction;
    }
}
