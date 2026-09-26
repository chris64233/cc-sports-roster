package com.chris64233.cc.sportsroster.domain;

/**
 * 转会申请状态机：
 * CREATED → AWAITING_CONFIRMATION → CONFIRMED → EXECUTED
 * 任一方拒绝则进入 REJECTED；执行时规则不满足或名单已变化则进入 FAILED，三者均为终态。
 */
public enum TransferStatus {
    CREATED,
    AWAITING_CONFIRMATION,
    CONFIRMED,
    REJECTED,
    FAILED,
    EXECUTED;

    public boolean isTerminal() {
        return this == REJECTED || this == FAILED || this == EXECUTED;
    }
}
