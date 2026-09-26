package com.chris64233.cc.sportsroster.domain;

/**
 * 球员在一笔交换申请中的流动方向。
 * FROM_TO：原球队（fromTeam）→ 目标球队（toTeam）
 * TO_FROM：目标球队（toTeam）→ 原球队（fromTeam）
 */
public enum TransferDirection {
    FROM_TO,
    TO_FROM
}
