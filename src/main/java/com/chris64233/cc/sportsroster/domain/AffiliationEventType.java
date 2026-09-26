package com.chris64233.cc.sportsroster.domain;

/**
 * 球员赛季归属时间线事件类型。
 * REGISTERED：赛季名单注册时归属（历史回填）
 * TRANSFER_OUT：转会执行时离开原队
 * TRANSFER_IN：转会执行时加入新队
 */
public enum AffiliationEventType {
    REGISTERED,
    TRANSFER_OUT,
    TRANSFER_IN
}
