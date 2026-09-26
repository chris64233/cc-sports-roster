package com.chris64233.cc.sportsroster.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 转会执行结果。executed=false 时 failureViolations 给出全部失败规则明细，
 * snapshots 仅包含执行前快照；executed=true 时包含双方执行前/后四份快照。
 */
public record TransferExecutionResponse(
        String applicationNo,
        String status,
        boolean executed,
        boolean replayed,
        ViolationResponse failureViolations,
        List<RosterSnapshotResponse> snapshots,
        LocalDateTime executedAt) {
}
