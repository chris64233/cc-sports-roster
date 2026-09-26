package com.chris64233.cc.sportsroster.domain;

/**
 * 名单快照拍摄时机：执行前（BEFORE）与执行后（AFTER）。
 * 执行失败时只保留 BEFORE 快照，原注册保持不变。
 */
public enum SnapshotPhase {
    BEFORE,
    AFTER
}
