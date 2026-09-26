package com.chris64233.cc.sportsroster.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.sportsroster.api.AffiliationEventResponse;
import com.chris64233.cc.sportsroster.api.CreateTransferRequest;
import com.chris64233.cc.sportsroster.api.RosterSnapshotResponse;
import com.chris64233.cc.sportsroster.api.TransferDecisionRequest;
import com.chris64233.cc.sportsroster.api.TransferDecisionResponse;
import com.chris64233.cc.sportsroster.api.TransferExecutionResponse;
import com.chris64233.cc.sportsroster.api.TransferItemRequest;
import com.chris64233.cc.sportsroster.api.TransferProgressResponse;
import com.chris64233.cc.sportsroster.api.Violation;
import com.chris64233.cc.sportsroster.api.ViolationResponse;
import com.chris64233.cc.sportsroster.domain.AffiliationEventType;
import com.chris64233.cc.sportsroster.domain.Player;
import com.chris64233.cc.sportsroster.domain.PlayerAffiliationEvent;
import com.chris64233.cc.sportsroster.domain.Roster;
import com.chris64233.cc.sportsroster.domain.RosterEntry;
import com.chris64233.cc.sportsroster.domain.RosterSnapshot;
import com.chris64233.cc.sportsroster.domain.Season;
import com.chris64233.cc.sportsroster.domain.SnapshotPhase;
import com.chris64233.cc.sportsroster.domain.Team;
import com.chris64233.cc.sportsroster.domain.TransferApplication;
import com.chris64233.cc.sportsroster.domain.TransferDecision;
import com.chris64233.cc.sportsroster.domain.TransferDecisionEvent;
import com.chris64233.cc.sportsroster.domain.TransferDirection;
import com.chris64233.cc.sportsroster.domain.TransferItem;
import com.chris64233.cc.sportsroster.domain.TransferStatus;
import com.chris64233.cc.sportsroster.exception.BadRequestException;
import com.chris64233.cc.sportsroster.exception.IdempotencyConflictException;
import com.chris64233.cc.sportsroster.exception.RegistrationCommitConflictException;
import com.chris64233.cc.sportsroster.exception.ResourceNotFoundException;
import com.chris64233.cc.sportsroster.exception.TransferStateException;
import com.chris64233.cc.sportsroster.repo.PlayerAffiliationEventRepository;
import com.chris64233.cc.sportsroster.repo.PlayerRepository;
import com.chris64233.cc.sportsroster.repo.RosterEntryRepository;
import com.chris64233.cc.sportsroster.repo.RosterRepository;
import com.chris64233.cc.sportsroster.repo.RosterSnapshotRepository;
import com.chris64233.cc.sportsroster.repo.SeasonRepository;
import com.chris64233.cc.sportsroster.repo.TeamRepository;
import com.chris64233.cc.sportsroster.repo.TransferApplicationRepository;
import com.chris64233.cc.sportsroster.repo.TransferDecisionEventRepository;
import com.chris64233.cc.sportsroster.repo.TransferItemRepository;

/**
 * 赛季内球员转会（交换）服务。
 *
 * <p>执行路径在单个事务内完成：锁定申请 → 锁定双方名单（核对冻结版本）→
 * 锁定涉及球员行（并发争抢串行化）→ 收集窗口/禁赛/队籍/规则全部违规 →
 * 拍摄执行前快照 → 原子迁移注册条目 → 写入归属时间线 → 拍摄执行后快照 →
 * 申请置为 EXECUTED。任一前置条件或规则不满足，申请置为 FAILED（终态）、
 * 保留执行前快照并返回全部违规明细，原注册保持不变。
 */
@Service
public class TransferService {

    private final TransferApplicationRepository applicationRepository;
    private final TransferItemRepository itemRepository;
    private final TransferDecisionEventRepository decisionEventRepository;
    private final RosterSnapshotRepository snapshotRepository;
    private final PlayerAffiliationEventRepository affiliationEventRepository;
    private final RosterRepository rosterRepository;
    private final RosterEntryRepository entryRepository;
    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final SeasonRepository seasonRepository;
    private final TransferRulesValidator rulesValidator;
    private final TransferResponseAssembler assembler;
    private final SnapshotCapturer snapshotCapturer;
    private final TransferIdempotencyQueries idempotencyQueries;
    private final TransferApplicationCommands applicationCommands;
    private final EntityManager entityManager;
    private final Clock clock;

    public TransferService(TransferApplicationRepository applicationRepository,
                           TransferItemRepository itemRepository,
                           TransferDecisionEventRepository decisionEventRepository,
                           RosterSnapshotRepository snapshotRepository,
                           PlayerAffiliationEventRepository affiliationEventRepository,
                           RosterRepository rosterRepository,
                           RosterEntryRepository entryRepository,
                           PlayerRepository playerRepository,
                           TeamRepository teamRepository,
                           SeasonRepository seasonRepository,
                           TransferRulesValidator rulesValidator,
                           TransferResponseAssembler assembler,
                           SnapshotCapturer snapshotCapturer,
                           TransferIdempotencyQueries idempotencyQueries,
                           TransferApplicationCommands applicationCommands,
                           EntityManager entityManager,
                           Clock clock) {
        this.applicationRepository = applicationRepository;
        this.itemRepository = itemRepository;
        this.decisionEventRepository = decisionEventRepository;
        this.snapshotRepository = snapshotRepository;
        this.affiliationEventRepository = affiliationEventRepository;
        this.rosterRepository = rosterRepository;
        this.entryRepository = entryRepository;
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.seasonRepository = seasonRepository;
        this.rulesValidator = rulesValidator;
        this.assembler = assembler;
        this.snapshotCapturer = snapshotCapturer;
        this.idempotencyQueries = idempotencyQueries;
        this.applicationCommands = applicationCommands;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // 申请创建（申请号幂等）
    // ------------------------------------------------------------------

    @Transactional
    public TransferProgressResponse createApplication(CreateTransferRequest request) {
        String contentHash = contentHash(request);
        Optional<TransferApplication> existing =
                applicationRepository.findByApplicationNo(request.applicationNo());
        if (existing.isPresent()) {
            return replayProgress(existing.get(), contentHash);
        }

        Season season = seasonRepository.findById(request.seasonId())
                .orElseThrow(() -> new ResourceNotFoundException("赛季 " + request.seasonId() + " 不存在"));
        Team fromTeam = teamRepository.findById(request.fromTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("原球队 " + request.fromTeamId() + " 不存在"));
        Team toTeam = teamRepository.findById(request.toTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("目标球队 " + request.toTeamId() + " 不存在"));
        if (fromTeam.getId().equals(toTeam.getId())) {
            throw new BadRequestException("原球队与目标球队不能相同");
        }
        if (request.effectiveDate() == null) {
            throw new BadRequestException("期望生效日不能为空");
        }
        // 注意：转会窗口在执行时校验（TRANSFER_WINDOW_CLOSED），申请创建时只冻结名单版本

        List<TransferItemRequest> itemRequests = request.items();
        List<Long> playerIds = itemRequests.stream().map(TransferItemRequest::playerId).distinct().toList();
        if (playerIds.size() != itemRequests.size()) {
            throw new BadRequestException("同一球员在一笔申请中不能出现多次");
        }
        if (itemRequests.stream().anyMatch(item -> item.direction() == null)) {
            throw new BadRequestException("交换方向不能为空");
        }
        List<Player> players = playerRepository.findByIdInOrderByCodeAsc(playerIds);
        if (players.size() != playerIds.size()) {
            throw new BadRequestException("申请包含不存在的球员");
        }

        // 冻结双方当前名单版本（按球队 id 顺序加锁，避免交叉死锁）
        List<Roster> rosters = lockRosters(season, fromTeam, toTeam);
        Roster fromRoster = rosterOf(rosters, fromTeam);
        Roster toRoster = rosterOf(rosters, toTeam);
        if (fromRoster == null || toRoster == null) {
            throw new BadRequestException("双方球队在该赛季都必须已存在名单才能发起转会");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        TransferApplication application = new TransferApplication(
                request.applicationNo(), season, fromTeam, toTeam, request.effectiveDate(),
                TransferStatus.CREATED, fromRoster.getVersion(), toRoster.getVersion(),
                contentHash, now);
        try {
            application = applicationCommands.insert(application);
        } catch (DataIntegrityViolationException ex) {
            // 申请号唯一约束冲突：并发创建，读取胜出申请并按重放处理
            TransferApplication winner = idempotencyQueries.findApplication(request.applicationNo())
                    .orElseThrow(RegistrationCommitConflictException::new);
            return replayProgress(winner, contentHash);
        }

        Map<Long, Player> playerById = new HashMap<>();
        players.forEach(player -> playerById.put(player.getId(), player));
        for (TransferItemRequest itemRequest : itemRequests) {
            itemRepository.save(new TransferItem(
                    application, playerById.get(itemRequest.playerId()), itemRequest.direction()));
        }
        entityManager.flush();

        return progressOf(application, false);
    }

    // ------------------------------------------------------------------
    // 双方决定（事件号幂等）
    // ------------------------------------------------------------------

    @Transactional
    public TransferDecisionResponse decide(String applicationNo, TransferDecisionRequest request) {
        Optional<TransferDecisionEvent> priorEvent =
                decisionEventRepository.findByEventKey(request.eventKey());
        if (priorEvent.isPresent()) {
            return replayDecision(priorEvent.get(), applicationNo, request);
        }

        TransferApplication application = applicationRepository
                .findByApplicationNoForUpdate(applicationNo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "转会申请 " + applicationNo + " 不存在"));
        if (application.getStatus().isTerminal()) {
            throw new TransferStateException("申请已处于终态 " + application.getStatus()
                    + "，不能再作出决定");
        }

        Long teamId = request.teamId();
        boolean isFrom = application.getFromTeam().getId().equals(teamId);
        boolean isTo = application.getToTeam().getId().equals(teamId);
        if (!isFrom && !isTo) {
            throw new BadRequestException("球队 " + teamId + " 不是该申请的当事方");
        }
        Team team = isFrom ? application.getFromTeam() : application.getToTeam();

        // 申请行已加悲观锁：同事件号/同球队的并发决定在此串行，可安全做存在性预检
        Optional<TransferDecisionEvent> teamPrior =
                decisionEventRepository.findByApplicationIdAndTeamId(application.getId(), teamId);
        if (teamPrior.isPresent()) {
            TransferDecisionEvent prior = teamPrior.get();
            if (prior.getDecision() == request.decision()) {
                return assembler.assembleDecision(application, prior, true);
            }
            throw new TransferStateException(
                    "球队 " + teamId + " 已对此申请作出过不同决定");
        }

        TransferDecisionEvent event = new TransferDecisionEvent(
                request.eventKey(), application, team, request.decision(),
                LocalDateTime.now(clock));
        try {
            event = decisionEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            // 理论上的兜底（事件号在其他申请上使用等），直接按提交冲突回滚
            throw new RegistrationCommitConflictException();
        }

        applyDecisionToApplication(application);
        entityManager.flush();
        return assembler.assembleDecision(application, event, false);
    }

    private void applyDecisionToApplication(TransferApplication application) {
        List<TransferDecisionEvent> events =
                decisionEventRepository.findByApplicationIdOrderByIdAsc(application.getId());
        boolean anyReject = events.stream()
                .anyMatch(event -> event.getDecision() == TransferDecision.REJECT);
        LocalDateTime now = LocalDateTime.now(clock);
        if (anyReject) {
            application.setStatus(TransferStatus.REJECTED);
            application.markDecided(now);
            return;
        }
        boolean fromConfirmed = events.stream().anyMatch(event ->
                event.getTeam().getId().equals(application.getFromTeam().getId())
                        && event.getDecision() == TransferDecision.CONFIRM);
        boolean toConfirmed = events.stream().anyMatch(event ->
                event.getTeam().getId().equals(application.getToTeam().getId())
                        && event.getDecision() == TransferDecision.CONFIRM);
        if (fromConfirmed && toConfirmed) {
            application.setStatus(TransferStatus.CONFIRMED);
            application.markDecided(now);
        } else {
            // 仅单方确认，决定流程尚未完成，不写 decidedAt
            application.setStatus(TransferStatus.AWAITING_CONFIRMATION);
        }
    }

    private TransferDecisionResponse replayDecision(TransferDecisionEvent event,
                                                    String applicationNo,
                                                    TransferDecisionRequest request) {
        TransferApplication application = event.getApplication();
        boolean sameRequest = application.getApplicationNo().equals(applicationNo)
                && event.getTeam().getId().equals(request.teamId())
                && event.getDecision() == request.decision();
        if (!sameRequest) {
            throw new IdempotencyConflictException("相同事件号提交的决定内容不同");
        }
        return assembler.assembleDecision(application, event, true);
    }

    // ------------------------------------------------------------------
    // 执行：原子交换
    // ------------------------------------------------------------------

    @Transactional
    public TransferExecutionResponse execute(String applicationNo) {
        TransferApplication application = applicationRepository
                .findByApplicationNoForUpdate(applicationNo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "转会申请 " + applicationNo + " 不存在"));

        if (application.getStatus().isTerminal()) {
            if (application.getStatus() == TransferStatus.REJECTED) {
                throw new TransferStateException("申请已被一方球队拒绝，不能执行");
            }
            // FAILED / EXECUTED：按幂等重放返回原始结果
            return buildExecutionResponse(application, true);
        }
        if (application.getStatus() != TransferStatus.CONFIRMED) {
            throw new TransferStateException(
                    "双方球队尚未全部确认，当前状态为 " + application.getStatus());
        }

        Season season = application.getSeason();
        Team fromTeam = application.getFromTeam();
        Team toTeam = application.getToTeam();
        LocalDate effectiveDate = application.getEffectiveDate();
        LocalDateTime now = LocalDateTime.now(clock);

        List<TransferItem> items =
                itemRepository.findByApplicationIdWithPlayer(application.getId());
        List<Violation> violations = new ArrayList<>();
        if (items.isEmpty()) {
            violations.add(Violation.of("TRANSFER_EMPTY", "转会申请不包含任何球员", Map.of()));
            return failApplication(application, violations, List.of());
        }

        // 1. 锁定双方名单并核对冻结版本
        List<Roster> rosters = lockRosters(season, fromTeam, toTeam);
        Roster fromRoster = rosterOf(rosters, fromTeam);
        Roster toRoster = rosterOf(rosters, toTeam);
        if (fromRoster == null || toRoster == null) {
            violations.add(Violation.of("ROSTER_MISSING",
                    "执行时双方球队必须都已存在赛季名单", Map.of()));
            return failApplication(application, violations, List.of());
        }
        if (fromRoster.getVersion() != application.getFromRosterVersion()) {
            violations.add(versionChanged("原球队", fromTeam,
                    application.getFromRosterVersion(), fromRoster.getVersion()));
        }
        if (toRoster.getVersion() != application.getToRosterVersion()) {
            violations.add(versionChanged("目标球队", toTeam,
                    application.getToRosterVersion(), toRoster.getVersion()));
        }

        // 2. 转会窗口（申请创建后窗口可能已关闭）
        requireWindowOpenViolation(season, effectiveDate, violations);

        // 3. 锁定涉及球员行，并发争抢同一球员在此串行化
        List<Long> playerIds = items.stream().map(item -> item.getPlayer().getId()).toList();
        List<Player> lockedPlayers = playerRepository.findByIdInForUpdate(playerIds);

        // 4. 禁赛阻断
        for (Player player : lockedPlayers) {
            LocalDate suspensionUntil = player.getSuspensionUntil();
            if (suspensionUntil != null && !suspensionUntil.isBefore(effectiveDate)) {
                violations.add(Violation.player("PLAYER_SUSPENDED",
                        "球员 " + player.getCode() + " 在生效日 " + effectiveDate
                                + " 处于禁赛期（禁赛截止 " + suspensionUntil + "）",
                        player.getId()));
            }
        }

        // 5. 队籍与声明方向一致
        List<RosterEntry> fromEntries =
                entryRepository.findByRosterIdWithPlayer(fromRoster.getId());
        List<RosterEntry> toEntries =
                entryRepository.findByRosterIdWithPlayer(toRoster.getId());
        Map<Long, RosterEntry> fromEntryByPlayer = indexByPlayer(fromEntries);
        Map<Long, RosterEntry> toEntryByPlayer = indexByPlayer(toEntries);
        List<Player> outFrom = new ArrayList<>();
        List<Player> outTo = new ArrayList<>();
        for (TransferItem item : items) {
            Player player = item.getPlayer();
            if (item.getDirection() == TransferDirection.FROM_TO) {
                if (fromEntryByPlayer.get(player.getId()) == null) {
                    violations.add(Violation.player("PLAYER_NOT_REGISTERED_WITH_TEAM",
                            "球员 " + player.getCode() + " 当前不在原球队 "
                                    + fromTeam.getCode() + " 的名单中", player.getId()));
                } else {
                    outFrom.add(player);
                }
            } else {
                if (toEntryByPlayer.get(player.getId()) == null) {
                    violations.add(Violation.player("PLAYER_NOT_REGISTERED_WITH_TEAM",
                            "球员 " + player.getCode() + " 当前不在目标球队 "
                                    + toTeam.getCode() + " 的名单中", player.getId()));
                } else {
                    outTo.add(player);
                }
            }
        }

        // 6. 前置条件全部满足后，才按交换后的完整名单判断规则
        if (violations.isEmpty()) {
            Map<Long, Player> resultingFrom =
                    indexPlayers(fromEntries.stream().map(RosterEntry::getPlayer).toList());
            Map<Long, Player> resultingTo =
                    indexPlayers(toEntries.stream().map(RosterEntry::getPlayer).toList());
            outFrom.forEach(player -> resultingFrom.remove(player.getId()));
            outTo.forEach(player -> resultingTo.remove(player.getId()));
            outTo.forEach(player -> resultingFrom.put(player.getId(), player));
            outFrom.forEach(player -> resultingTo.put(player.getId(), player));

            violations.addAll(rulesValidator.validateCompletedRoster(
                    season, fromTeam, effectiveDate, new ArrayList<>(resultingFrom.values())));
            violations.addAll(rulesValidator.validateCompletedRoster(
                    season, toTeam, effectiveDate, new ArrayList<>(resultingTo.values())));
        }

        if (!violations.isEmpty()) {
            List<RosterSnapshot> beforeSnapshots =
                    captureBeforeSnapshots(application, fromRoster, toRoster, now);
            return failApplication(application, violations, beforeSnapshots);
        }

        // 全部通过：执行前快照 → 历史回填 → 原子迁移 → 时间线 → 执行后快照
        List<RosterSnapshot> beforeSnapshots =
                captureBeforeSnapshots(application, fromRoster, toRoster, now);
        backfillRegistrationEvents(season, fromEntries);
        backfillRegistrationEvents(season, toEntries);

        try {
            for (TransferItem item : items) {
                RosterEntry entry = item.getDirection() == TransferDirection.FROM_TO
                        ? fromEntryByPlayer.get(item.getPlayer().getId())
                        : toEntryByPlayer.get(item.getPlayer().getId());
                Roster target = item.getDirection() == TransferDirection.FROM_TO ? toRoster : fromRoster;
                entry.moveTo(target);
            }
            fromRoster.setSubmittedAt(now);
            toRoster.setSubmittedAt(now);
            entityManager.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new RegistrationCommitConflictException();
        }

        for (TransferItem item : items) {
            Player player = item.getPlayer();
            Team sourceTeam = item.getDirection() == TransferDirection.FROM_TO ? fromTeam : toTeam;
            Team targetTeam = item.getDirection() == TransferDirection.FROM_TO ? toTeam : fromTeam;
            affiliationEventRepository.save(new PlayerAffiliationEvent(
                    season, player, sourceTeam, AffiliationEventType.TRANSFER_OUT,
                    application, effectiveDate, now));
            affiliationEventRepository.save(new PlayerAffiliationEvent(
                    season, player, targetTeam, AffiliationEventType.TRANSFER_IN,
                    application, effectiveDate, now));
        }

        application.setStatus(TransferStatus.EXECUTED);
        application.markExecuted(now);
        application.setFailureDetails(null);
        entityManager.flush();

        // 申请落库 EXECUTED 后拍摄，快照内可读取到递增后的名单版本
        List<RosterSnapshot> allSnapshots = new ArrayList<>(beforeSnapshots);
        allSnapshots.add(snapshotCapturer.capture(
                application, fromTeam, fromRoster, SnapshotPhase.AFTER, now));
        allSnapshots.add(snapshotCapturer.capture(
                application, toTeam, toRoster, SnapshotPhase.AFTER, now));
        entityManager.flush();

        return buildExecutionResponse(application, false, allSnapshots, null);
    }

    private Violation versionChanged(String label, Team team, long frozenVersion, long currentVersion) {
        return Violation.of("ROSTER_VERSION_CHANGED",
                label + "名单已变化：冻结版本 " + frozenVersion + "，当前版本 " + currentVersion,
                Map.of("teamId", team.getId(), "teamCode", team.getCode(),
                        "frozenVersion", frozenVersion, "currentVersion", currentVersion));
    }

    private List<RosterSnapshot> captureBeforeSnapshots(TransferApplication application,
                                                        Roster fromRoster, Roster toRoster,
                                                        LocalDateTime now) {
        if (snapshotRepository.existsByApplicationIdAndTeamIdAndPhase(
                application.getId(), application.getFromTeam().getId(), SnapshotPhase.BEFORE)) {
            return List.of();
        }
        List<RosterSnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshotCapturer.capture(
                application, application.getFromTeam(), fromRoster, SnapshotPhase.BEFORE, now));
        snapshots.add(snapshotCapturer.capture(
                application, application.getToTeam(), toRoster, SnapshotPhase.BEFORE, now));
        return snapshots;
    }

    /**
     * 在条目迁移之前把当前注册回填为 REGISTERED 时间线事件（只回填一次），
     * 保证球员的首个归属事件是其原始注册球队。
     */
    private void backfillRegistrationEvents(Season season, List<RosterEntry> entries) {
        for (RosterEntry entry : entries) {
            Player player = entry.getPlayer();
            if (affiliationEventRepository
                    .existsBySeasonIdAndPlayerIdAndEventTypeAndApplicationNull(
                            season.getId(), player.getId(), AffiliationEventType.REGISTERED)) {
                continue;
            }
            LocalDateTime registeredAt = entry.getRegisteredAt();
            affiliationEventRepository.save(new PlayerAffiliationEvent(
                    season, player, entry.getRoster().getTeam(),
                    AffiliationEventType.REGISTERED, null,
                    registeredAt.toLocalDate(), registeredAt));
        }
    }

    private TransferExecutionResponse failApplication(TransferApplication application,
                                                      List<Violation> violations,
                                                      List<RosterSnapshot> beforeSnapshots) {
        application.setStatus(TransferStatus.FAILED);
        ViolationResponse body = ViolationResponse.invalid(violations);
        application.setFailureDetails(JsonSupport.write(body));
        entityManager.flush();
        return buildExecutionResponse(application, false, beforeSnapshots, body);
    }

    private TransferExecutionResponse buildExecutionResponse(TransferApplication application,
                                                             boolean replayed) {
        List<RosterSnapshot> snapshots =
                snapshotRepository.findByApplicationIdOrderByTeamIdAscPhaseAsc(application.getId());
        ViolationResponse failureViolations = application.getFailureDetails() == null ? null
                : JsonSupport.read(application.getFailureDetails(), ViolationResponse.class);
        return buildExecutionResponse(application, replayed, snapshots, failureViolations);
    }

    private TransferExecutionResponse buildExecutionResponse(TransferApplication application,
                                                             boolean replayed,
                                                             List<RosterSnapshot> snapshots,
                                                             ViolationResponse failureViolations) {
        List<RosterSnapshotResponse> snapshotViews = snapshots.stream()
                .sorted(Comparator.comparing((RosterSnapshot snapshot) -> snapshot.getTeam().getId())
                        .thenComparing(snapshot -> snapshot.getPhase() == SnapshotPhase.BEFORE ? 0 : 1))
                .map(assembler::assembleSnapshot)
                .toList();
        return new TransferExecutionResponse(
                application.getApplicationNo(),
                application.getStatus().name(),
                application.getStatus() == TransferStatus.EXECUTED,
                replayed,
                failureViolations,
                snapshotViews,
                application.getExecutedAt());
    }

    // ------------------------------------------------------------------
    // 查询：进度 / 快照 / 时间线
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TransferProgressResponse getProgress(String applicationNo) {
        return progressOf(requireApplication(applicationNo), false);
    }

    @Transactional(readOnly = true)
    public List<RosterSnapshotResponse> getSnapshots(String applicationNo) {
        TransferApplication application = requireApplication(applicationNo);
        return snapshotRepository
                .findByApplicationIdOrderByTeamIdAscPhaseAsc(application.getId()).stream()
                .sorted(Comparator.comparing((RosterSnapshot snapshot) -> snapshot.getTeam().getId())
                        .thenComparing(snapshot -> snapshot.getPhase() == SnapshotPhase.BEFORE ? 0 : 1))
                .map(assembler::assembleSnapshot)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AffiliationEventResponse> getPlayerTimeline(Long seasonId, Long playerId) {
        if (!seasonRepository.existsById(seasonId)) {
            throw new ResourceNotFoundException("赛季 " + seasonId + " 不存在");
        }
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("球员 " + playerId + " 不存在");
        }
        return affiliationEventRepository.findTimeline(seasonId, playerId).stream()
                .map(assembler::assembleAffiliation)
                .toList();
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private TransferApplication requireApplication(String applicationNo) {
        return applicationRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "转会申请 " + applicationNo + " 不存在"));
    }

    private TransferProgressResponse progressOf(TransferApplication application, boolean replayed) {
        List<TransferItem> items =
                itemRepository.findByApplicationIdWithPlayer(application.getId());
        List<TransferDecisionEvent> decisions =
                decisionEventRepository.findByApplicationIdOrderByIdAsc(application.getId());
        return assembler.assembleProgress(application, items, decisions, replayed);
    }

    private TransferProgressResponse replayProgress(TransferApplication application,
                                                    String contentHash) {
        if (!application.getContentHash().equals(contentHash)) {
            throw new IdempotencyConflictException("相同申请号提交的转会内容不同");
        }
        return progressOf(application, true);
    }

    /**
     * 按球队 id 全局顺序对双方名单加悲观写锁，保证多笔并发转会不会交叉持锁形成死锁。
     */
    private List<Roster> lockRosters(Season season, Team first, Team second) {
        List<Long> teamIds = List.of(first.getId(), second.getId()).stream().sorted().toList();
        List<Roster> locked = new ArrayList<>();
        for (Long teamId : teamIds) {
            rosterRepository.findByTeamIdAndSeasonIdForUpdate(teamId, season.getId())
                    .ifPresent(locked::add);
        }
        return locked;
    }

    private Roster rosterOf(List<Roster> rosters, Team team) {
        return rosters.stream()
                .filter(roster -> roster.getTeam().getId().equals(team.getId()))
                .findFirst()
                .orElse(null);
    }

    private void requireWindowOpenViolation(Season season, LocalDate date,
                                            List<Violation> violations) {
        if (date.isBefore(season.getRegistrationStart())
                || date.isAfter(season.getRegistrationEnd())) {
            violations.add(Violation.of("TRANSFER_WINDOW_CLOSED",
                    "生效日 " + date + " 不在转会窗口 "
                            + season.getRegistrationStart() + " 至 " + season.getRegistrationEnd() + " 内",
                    Map.of("effectiveDate", date.toString(),
                            "windowStart", season.getRegistrationStart().toString(),
                            "windowEnd", season.getRegistrationEnd().toString())));
        }
    }

    private Map<Long, RosterEntry> indexByPlayer(List<RosterEntry> entries) {
        Map<Long, RosterEntry> map = new HashMap<>();
        for (RosterEntry entry : entries) {
            map.put(entry.getPlayer().getId(), entry);
        }
        return map;
    }

    private Map<Long, Player> indexPlayers(List<Player> players) {
        Map<Long, Player> map = new LinkedHashMap<>();
        for (Player player : players) {
            map.put(player.getId(), player);
        }
        return map;
    }

    private String contentHash(CreateTransferRequest request) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("seasonId", request.seasonId());
        canonical.put("fromTeamId", request.fromTeamId());
        canonical.put("toTeamId", request.toTeamId());
        canonical.put("effectiveDate", String.valueOf(request.effectiveDate()));
        List<Map<String, Object>> items = request.items().stream()
                .sorted(Comparator.comparing(TransferItemRequest::playerId))
                .map(item -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("playerId", item.playerId());
                    entry.put("direction", item.direction().name());
                    return entry;
                })
                .toList();
        canonical.put("items", items);
        String json = JsonSupport.write(canonical);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
