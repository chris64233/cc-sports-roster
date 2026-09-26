package com.chris64233.cc.sportsroster.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.cc.sportsroster.api.AffiliationEventResponse;
import com.chris64233.cc.sportsroster.api.CreateTransferRequest;
import com.chris64233.cc.sportsroster.api.RosterSnapshotResponse;
import com.chris64233.cc.sportsroster.api.TransferDecisionRequest;
import com.chris64233.cc.sportsroster.api.TransferDecisionResponse;
import com.chris64233.cc.sportsroster.api.TransferExecutionResponse;
import com.chris64233.cc.sportsroster.api.TransferProgressResponse;
import com.chris64233.cc.sportsroster.service.TransferService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * 创建转会（交换）申请。申请号幂等：相同申请号 + 相同内容重放返回原申请（replayed=true，200），
     * 内容不同返回 409；新申请返回 201。
     */
    @PostMapping
    public ResponseEntity<TransferProgressResponse> create(
            @Valid @RequestBody CreateTransferRequest request) {
        TransferProgressResponse response = transferService.createApplication(request);
        return ResponseEntity.status(response.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(response);
    }

    /**
     * 一方球队作出确认/拒绝决定。事件号幂等；任一方拒绝申请即 REJECTED，
     * 双方均确认后申请进入 CONFIRMED，可执行。
     */
    @PostMapping("/{applicationNo}/decisions")
    public TransferDecisionResponse decide(@PathVariable String applicationNo,
                                           @Valid @RequestBody TransferDecisionRequest request) {
        return transferService.decide(applicationNo, request);
    }

    /**
     * 执行原子交换。成功 200；规则不满足或名单已变化时申请终态 FAILED，返回 422 与全部违规明细，
     * 原注册保持不变；终态申请重复执行返回原结果（replayed=true）。
     */
    @PostMapping("/{applicationNo}/execute")
    public ResponseEntity<TransferExecutionResponse> execute(@PathVariable String applicationNo) {
        TransferExecutionResponse response = transferService.execute(applicationNo);
        return ResponseEntity.status("EXECUTED".equals(response.status())
                        ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
                .body(response);
    }

    /** 转会进度：状态、冻结版本、双方确认情况、球员清单与失败规则明细。 */
    @GetMapping("/{applicationNo}")
    public TransferProgressResponse progress(@PathVariable String applicationNo) {
        return transferService.getProgress(applicationNo);
    }

    /** 执行前后双方名单快照；失败时只有执行前快照。 */
    @GetMapping("/{applicationNo}/snapshots")
    public List<RosterSnapshotResponse> snapshots(@PathVariable String applicationNo) {
        return transferService.getSnapshots(applicationNo);
    }

    /** 球员在指定赛季的归属时间线（注册 / 转出 / 转入）。 */
    @GetMapping("/seasons/{seasonId}/players/{playerId}/timeline")
    public List<AffiliationEventResponse> timeline(@PathVariable Long seasonId,
                                                   @PathVariable Long playerId) {
        return transferService.getPlayerTimeline(seasonId, playerId);
    }
}
