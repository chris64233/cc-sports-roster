package com.chris64233.cc.sportsroster.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.chris64233.cc.sportsroster.api.AffiliationEventResponse;
import com.chris64233.cc.sportsroster.api.CreateTransferRequest;
import com.chris64233.cc.sportsroster.api.TransferDecisionRequest;
import com.chris64233.cc.sportsroster.api.TransferExecutionResponse;
import com.chris64233.cc.sportsroster.api.TransferItemRequest;
import com.chris64233.cc.sportsroster.api.TransferProgressResponse;
import com.chris64233.cc.sportsroster.api.Violation;
import com.chris64233.cc.sportsroster.domain.AffiliationEventType;
import com.chris64233.cc.sportsroster.domain.Player;
import com.chris64233.cc.sportsroster.domain.Roster;
import com.chris64233.cc.sportsroster.domain.RosterEntry;
import com.chris64233.cc.sportsroster.domain.Season;
import com.chris64233.cc.sportsroster.domain.Team;
import com.chris64233.cc.sportsroster.domain.TransferDecision;
import com.chris64233.cc.sportsroster.domain.TransferDirection;
import com.chris64233.cc.sportsroster.repo.PlayerRepository;
import com.chris64233.cc.sportsroster.repo.RosterEntryRepository;
import com.chris64233.cc.sportsroster.repo.RosterRepository;
import com.chris64233.cc.sportsroster.repo.SeasonRepository;
import com.chris64233.cc.sportsroster.repo.TeamRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransferServiceTest {

    private static final LocalDate WINDOW_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 8, 31);
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 6, 15);
    private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 2, 1, 10, 0);

    @Autowired
    private TransferService transferService;
    @Autowired
    private SeasonRepository seasonRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private RosterRepository rosterRepository;
    @Autowired
    private RosterEntryRepository entryRepository;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : List.of(
                "player_affiliation_event", "roster_snapshot", "transfer_decision_event",
                "transfer_item", "transfer_application", "roster_entry", "roster",
                "player", "team", "season", "idempotent_request")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private Season season;
    private Team teamA;
    private Team teamB;
    private Team teamC;

    private Player mkPlayer(String code, String nationality, boolean homegrown,
                            LocalDate suspensionUntil) {
        return playerRepository.save(new Player(code, "球员" + code, LocalDate.of(2000, 1, 1),
                nationality, homegrown, suspensionUntil));
    }

    private Player mkPlayer(String code) {
        return mkPlayer(code, "CN", true, null);
    }

    private Team mkTeam(String code) {
        return teamRepository.save(new Team(code, "球队" + code));
    }

    private Season mkSeason(int rosterMax, int foreignMax, int homegrownMin) {
        return seasonRepository.save(new Season("S-" + UUID.randomUUID(), "赛季",
                WINDOW_START, WINDOW_END, rosterMax, foreignMax, homegrownMin, "CN"));
    }

    private void createRoster(Team team, Season targetSeason, List<Player> players) {
        Roster roster = rosterRepository.save(new Roster(team, targetSeason, REGISTERED_AT));
        for (Player player : players) {
            entryRepository.save(new RosterEntry(roster, targetSeason, player, REGISTERED_AT));
        }
    }

    private void setupStandardFixture(int rosterMax, int foreignMax, int homegrownMin) {
        season = mkSeason(rosterMax, foreignMax, homegrownMin);
        teamA = mkTeam("A");
        teamB = mkTeam("B");
        teamC = mkTeam("C");
    }

    private CreateTransferRequest request(Team from, Team to, List<TransferItemRequest> items) {
        return new CreateTransferRequest(season.getId(), from.getId(), to.getId(),
                EFFECTIVE_DATE, items, "TA-" + UUID.randomUUID());
    }

    private TransferItemRequest item(Player player, TransferDirection direction) {
        return new TransferItemRequest(player.getId(), direction);
    }

    private void confirmBoth(String applicationNo, Team from, Team to) {
        transferService.decide(applicationNo, new TransferDecisionRequest(
                from.getId(), TransferDecision.CONFIRM, "EV-" + UUID.randomUUID()));
        transferService.decide(applicationNo, new TransferDecisionRequest(
                to.getId(), TransferDecision.CONFIRM, "EV-" + UUID.randomUUID()));
    }

    private void assertPlayersOf(Team team, Season targetSeason, List<Player> expected) {
        Roster roster = rosterRepository
                .findByTeamIdAndSeasonId(team.getId(), targetSeason.getId()).orElseThrow();
        List<String> codes = entryRepository.findByRosterIdWithPlayer(roster.getId()).stream()
                .map(entry -> entry.getPlayer().getCode())
                .sorted()
                .toList();
        assertThat(codes).isEqualTo(expected.stream()
                .map(Player::getCode).sorted().toList());
    }

    private List<Integer> rosterSizes() {
        return List.of(teamA, teamB).stream()
                .map(team -> rosterRepository.findByTeamIdAndSeasonId(team.getId(), season.getId())
                        .orElseThrow())
                .map(roster -> entryRepository.findByRosterId(roster.getId()).size())
                .toList();
    }

    // ------------------------------------------------------------------
    // 成功路径
    // ------------------------------------------------------------------

    @Test
    void atomicSinglePlayerTransferSucceedsAndBumpsOnlyTargetRosterVersion() {
        setupStandardFixture(5, 3, 1);
        Player p1 = mkPlayer("P1");
        Player p2 = mkPlayer("P2");
        Player p3 = mkPlayer("P3");
        createRoster(teamA, season, List.of(p1, p2));
        createRoster(teamB, season, List.of(p3));
        long aVersionBefore = rosterRepository
                .findByTeamIdAndSeasonId(teamA.getId(), season.getId()).orElseThrow().getVersion();
        long bVersionBefore = rosterRepository
                .findByTeamIdAndSeasonId(teamB.getId(), season.getId()).orElseThrow().getVersion();

        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(p1, TransferDirection.FROM_TO))));
        assertThat(created.status()).isEqualTo("CREATED");
        assertThat(created.fromRosterVersion()).isEqualTo(aVersionBefore);
        assertThat(created.toRosterVersion()).isEqualTo(bVersionBefore);
        assertThat(created.replayed()).isFalse();

        confirmBoth(created.applicationNo(), teamA, teamB);
        TransferExecutionResponse result = transferService.execute(created.applicationNo());

        assertThat(result.executed()).isTrue();
        assertThat(result.status()).isEqualTo("EXECUTED");
        assertThat(result.failureViolations()).isNull();
        assertPlayersOf(teamA, season, List.of(p2));
        assertPlayersOf(teamB, season, List.of(p3, p1));

        long aVersionAfter = rosterRepository
                .findByTeamIdAndSeasonId(teamA.getId(), season.getId()).orElseThrow().getVersion();
        long bVersionAfter = rosterRepository
                .findByTeamIdAndSeasonId(teamB.getId(), season.getId()).orElseThrow().getVersion();
        assertThat(aVersionAfter).isEqualTo(aVersionBefore + 1);
        assertThat(bVersionAfter).isEqualTo(bVersionBefore + 1);
    }

    @Test
    void multiPlayerSwapValidatedOnlyAgainstCompletedRosters() {
        // 上限 3 人：B 队当前满员（3 人），若逐个加入会暂时达到 4 人；
        // 2 换 2 后双方仍为 3 人，按交换后的完整名单必须成功。
        setupStandardFixture(3, 3, 0);
        Player a1 = mkPlayer("A1");
        Player a2 = mkPlayer("A2");
        Player a3 = mkPlayer("A3");
        Player b1 = mkPlayer("B1");
        Player b2 = mkPlayer("B2");
        Player b3 = mkPlayer("B3");
        createRoster(teamA, season, List.of(a1, a2, a3));
        createRoster(teamB, season, List.of(b1, b2, b3));

        var created = transferService.createApplication(request(teamA, teamB, List.of(
                item(a1, TransferDirection.FROM_TO),
                item(a2, TransferDirection.FROM_TO),
                item(b1, TransferDirection.TO_FROM),
                item(b2, TransferDirection.TO_FROM))));
        confirmBoth(created.applicationNo(), teamA, teamB);
        TransferExecutionResponse result = transferService.execute(created.applicationNo());

        assertThat(result.executed()).isTrue();
        assertPlayersOf(teamA, season, List.of(a3, b1, b2));
        assertPlayersOf(teamB, season, List.of(b3, a1, a2));
        assertThat(rosterSizes()).containsExactly(3, 3);
    }

    // ------------------------------------------------------------------
    // 失败回滚
    // ------------------------------------------------------------------

    @Test
    void targetRosterOverLimitFailsWholeTransferAndKeepsOriginalRegistrations() {
        setupStandardFixture(3, 3, 0);
        Player a1 = mkPlayer("A1");
        Player b1 = mkPlayer("B1");
        Player b2 = mkPlayer("B2");
        Player b3 = mkPlayer("B3");
        createRoster(teamA, season, List.of(a1));
        createRoster(teamB, season, List.of(b1, b2, b3));

        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(a1, TransferDirection.FROM_TO))));
        confirmBoth(created.applicationNo(), teamA, teamB);
        TransferExecutionResponse result = transferService.execute(created.applicationNo());

        assertThat(result.executed()).isFalse();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.failureViolations().valid()).isFalse();
        assertThat(result.failureViolations().violations())
                .extracting(Violation::code)
                .contains("ROSTER_SIZE_EXCEEDED");
        // 原注册保持不变
        assertPlayersOf(teamA, season, List.of(a1));
        assertPlayersOf(teamB, season, List.of(b1, b2, b3));
        // 失败为终态，再次执行返回同一失败结果
        TransferExecutionResponse replay = transferService.execute(created.applicationNo());
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.status()).isEqualTo("FAILED");
    }

    @Test
    void foreignLimitViolationIsReportedWithDetails() {
        setupStandardFixture(10, 1, 0);
        Player aForeign1 = mkPlayer("AF", "US", false, null);
        Player aHome = mkPlayer("AH");
        Player bForeign = mkPlayer("BF", "US", false, null);
        createRoster(teamA, season, List.of(aForeign1, aHome));
        createRoster(teamB, season, List.of(bForeign));

        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(aForeign1, TransferDirection.FROM_TO))));
        confirmBoth(created.applicationNo(), teamA, teamB);
        TransferExecutionResponse result = transferService.execute(created.applicationNo());

        assertThat(result.executed()).isFalse();
        Violation violation = result.failureViolations().violations().stream()
                .filter(v -> v.code().equals("FOREIGN_PLAYER_LIMIT_EXCEEDED"))
                .findFirst().orElseThrow();
        assertThat(violation.details()).containsEntry("actual", 2L).containsEntry("limit", 1);
        assertPlayersOf(teamA, season, List.of(aForeign1, aHome));
        assertPlayersOf(teamB, season, List.of(bForeign));
    }

    @Test
    void homegrownMinimumViolationFailsAtomic() {
        setupStandardFixture(10, 10, 2);
        Player aHome1 = mkPlayer("AH1");
        Player aHome2 = mkPlayer("AH2");
        Player bForeign = mkPlayer("BF", "US", false, null);
        createRoster(teamA, season, List.of(aHome1, aHome2));
        createRoster(teamB, season, List.of(bForeign));

        var created = transferService.createApplication(request(teamA, teamB, List.of(
                item(aHome1, TransferDirection.FROM_TO),
                item(bForeign, TransferDirection.TO_FROM))));
        confirmBoth(created.applicationNo(), teamA, teamB);
        TransferExecutionResponse result = transferService.execute(created.applicationNo());

        assertThat(result.executed()).isFalse();
        assertThat(result.failureViolations().violations())
                .extracting(Violation::code)
                .contains("HOMEGROWN_MINIMUM_NOT_MET");
        assertPlayersOf(teamA, season, List.of(aHome1, aHome2));
        assertPlayersOf(teamB, season, List.of(bForeign));
    }

    // ------------------------------------------------------------------
    // 前置条件：窗口、禁赛、确认、名单版本
    // ------------------------------------------------------------------

    @Test
    void applicationOutsideWindowCanBeCreatedButExecutionFailsWithViolation() {
        setupStandardFixture(10, 10, 0);
        Player p1 = mkPlayer("P1");
        createRoster(teamA, season, List.of(p1));
        createRoster(teamB, season, List.of());
        LocalDate outsideWindow = WINDOW_END.plusDays(1);
        CreateTransferRequest request = new CreateTransferRequest(
                season.getId(), teamA.getId(), teamB.getId(), outsideWindow,
                List.of(item(p1, TransferDirection.FROM_TO)), "TA-" + UUID.randomUUID());

        // 创建不卡窗口（仍冻结名单版本）
        var created = transferService.createApplication(request);
        assertThat(created.status()).isEqualTo("CREATED");

        confirmBoth(created.applicationNo(), teamA, teamB);
        TransferExecutionResponse result = transferService.execute(created.applicationNo());
        assertThat(result.executed()).isFalse();
        assertThat(result.failureViolations().violations())
                .extracting(Violation::code)
                .contains("TRANSFER_WINDOW_CLOSED");
        assertPlayersOf(teamA, season, List.of(p1));
    }

    @Test
    void suspendedPlayerBlocksExecutionWithViolation() {
        setupStandardFixture(10, 10, 0);
        Player suspended = mkPlayer("S1", "CN", true, LocalDate.of(2026, 7, 1));
        Player p2 = mkPlayer("P2");
        createRoster(teamA, season, List.of(suspended));
        createRoster(teamB, season, List.of(p2));

        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(suspended, TransferDirection.FROM_TO))));
        confirmBoth(created.applicationNo(), teamA, teamB);
        TransferExecutionResponse result = transferService.execute(created.applicationNo());

        assertThat(result.executed()).isFalse();
        assertThat(result.failureViolations().violations())
                .extracting(Violation::code)
                .contains("PLAYER_SUSPENDED");
        assertPlayersOf(teamA, season, List.of(suspended));
    }

    @Test
    void cannotExecuteBeforeBothTeamsConfirm() {
        setupStandardFixture(10, 10, 0);
        Player p1 = mkPlayer("P1");
        Player p2 = mkPlayer("P2");
        createRoster(teamA, season, List.of(p1));
        createRoster(teamB, season, List.of(p2));

        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(p1, TransferDirection.FROM_TO))));
        transferService.decide(created.applicationNo(), new TransferDecisionRequest(
                teamA.getId(), TransferDecision.CONFIRM, "EV-" + UUID.randomUUID()));

        assertThatThrownBy(() -> transferService.execute(created.applicationNo()))
                .hasMessageContaining("尚未全部确认");
        assertPlayersOf(teamA, season, List.of(p1));
        assertPlayersOf(teamB, season, List.of(p2));
    }

    @Test
    void rejectionByEitherPartyMovesApplicationToRejected() {
        setupStandardFixture(10, 10, 0);
        Player p1 = mkPlayer("P1");
        Player p2 = mkPlayer("P2");
        createRoster(teamA, season, List.of(p1));
        createRoster(teamB, season, List.of(p2));

        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(p1, TransferDirection.FROM_TO))));
        var decision = transferService.decide(created.applicationNo(),
                new TransferDecisionRequest(teamB.getId(), TransferDecision.REJECT,
                        "EV-" + UUID.randomUUID()));
        assertThat(decision.applicationStatus()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> transferService.execute(created.applicationNo()))
                .hasMessageContaining("拒绝");
    }

    @Test
    void frozenRosterVersionChangeFailsTransfer() {
        setupStandardFixture(10, 10, 0);
        Player a1 = mkPlayer("A1");
        Player a2 = mkPlayer("A2");
        Player b1 = mkPlayer("B1");
        createRoster(teamA, season, List.of(a1));
        createRoster(teamB, season, List.of(b1));

        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(a1, TransferDirection.FROM_TO))));

        // 申请创建后，A 队名单发生变化（直接追加注册条目并推进版本）
        tx.executeWithoutResult(status -> {
            Roster rosterA = rosterRepository
                    .findByTeamIdAndSeasonId(teamA.getId(), season.getId()).orElseThrow();
            rosterA.setSubmittedAt(LocalDateTime.of(2026, 3, 1, 9, 0));
            entryRepository.save(new RosterEntry(rosterA, season, a2,
                    LocalDateTime.of(2026, 3, 1, 9, 0)));
        });

        confirmBoth(created.applicationNo(), teamA, teamB);
        TransferExecutionResponse result = transferService.execute(created.applicationNo());

        assertThat(result.executed()).isFalse();
        assertThat(result.failureViolations().violations())
                .extracting(Violation::code)
                .contains("ROSTER_VERSION_CHANGED");
        assertPlayersOf(teamA, season, List.of(a1, a2));
        assertPlayersOf(teamB, season, List.of(b1));
    }

    @Test
    void playerMustCurrentlyBelongToDeclaredSourceTeam() {
        setupStandardFixture(10, 10, 0);
        Player a1 = mkPlayer("A1");
        Player b1 = mkPlayer("B1");
        createRoster(teamA, season, List.of(a1));
        createRoster(teamB, season, List.of(b1));

        // 声明 a1 由 B 队交出，但 a1 实际在 A 队
        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(a1, TransferDirection.TO_FROM))));
        confirmBoth(created.applicationNo(), teamA, teamB);
        TransferExecutionResponse result = transferService.execute(created.applicationNo());

        assertThat(result.executed()).isFalse();
        assertThat(result.failureViolations().violations())
                .extracting(Violation::code)
                .contains("PLAYER_NOT_REGISTERED_WITH_TEAM");
        assertPlayersOf(teamA, season, List.of(a1));
        assertPlayersOf(teamB, season, List.of(b1));
    }

    // ------------------------------------------------------------------
    // 幂等
    // ------------------------------------------------------------------

    @Test
    void applicationNoIsIdempotentForSameContent() {
        setupStandardFixture(10, 10, 0);
        Player p1 = mkPlayer("P1");
        Player p2 = mkPlayer("P2");
        createRoster(teamA, season, List.of(p1));
        createRoster(teamB, season, List.of(p2));

        CreateTransferRequest first = request(teamA, teamB,
                List.of(item(p1, TransferDirection.FROM_TO)));
        var created1 = transferService.createApplication(first);
        var created2 = transferService.createApplication(first);

        assertThat(created2.replayed()).isTrue();
        assertThat(created2.applicationNo()).isEqualTo(created1.applicationNo());
        assertThat(created2.status()).isEqualTo(created1.status());
    }

    @Test
    void sameApplicationNoWithDifferentContentConflicts() {
        setupStandardFixture(10, 10, 0);
        Player p1 = mkPlayer("P1");
        Player p2 = mkPlayer("P2");
        createRoster(teamA, season, List.of(p1, p2));
        createRoster(teamB, season, List.of());
        String applicationNo = "TA-DUP-" + UUID.randomUUID();

        transferService.createApplication(new CreateTransferRequest(
                season.getId(), teamA.getId(), teamB.getId(), EFFECTIVE_DATE,
                List.of(item(p1, TransferDirection.FROM_TO)), applicationNo));
        assertThatThrownBy(() -> transferService.createApplication(new CreateTransferRequest(
                season.getId(), teamA.getId(), teamB.getId(), EFFECTIVE_DATE,
                List.of(item(p2, TransferDirection.FROM_TO)), applicationNo)))
                .hasMessageContaining("申请号");
    }

    @Test
    void decisionEventIsIdempotent() {
        setupStandardFixture(10, 10, 0);
        Player p1 = mkPlayer("P1");
        Player p2 = mkPlayer("P2");
        createRoster(teamA, season, List.of(p1));
        createRoster(teamB, season, List.of(p2));

        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(p1, TransferDirection.FROM_TO))));
        String eventKey = "EV-DUP-" + UUID.randomUUID();
        TransferDecisionRequest decision = new TransferDecisionRequest(
                teamA.getId(), TransferDecision.CONFIRM, eventKey);
        var first = transferService.decide(created.applicationNo(), decision);
        var second = transferService.decide(created.applicationNo(), decision);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.eventKey()).isEqualTo(eventKey);

        // 同事件号不同内容 -> 冲突
        assertThatThrownBy(() -> transferService.decide(created.applicationNo(),
                new TransferDecisionRequest(teamB.getId(), TransferDecision.CONFIRM, eventKey)))
                .isInstanceOf(RuntimeException.class);
    }

    // ------------------------------------------------------------------
    // 快照与时间线
    // ------------------------------------------------------------------

    @Test
    void snapshotsCaptureBeforeAndAfterOnSuccessAndBeforeOnlyOnFailure() {
        setupStandardFixture(3, 3, 0);
        Player a1 = mkPlayer("A1");
        Player b1 = mkPlayer("B1");
        Player b2 = mkPlayer("B2");
        Player b3 = mkPlayer("B3");
        createRoster(teamA, season, List.of(a1));
        createRoster(teamB, season, List.of(b1, b2, b3));

        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(a1, TransferDirection.FROM_TO))));
        confirmBoth(created.applicationNo(), teamA, teamB);
        transferService.execute(created.applicationNo());

        var failedSnapshots = transferService.getSnapshots(created.applicationNo());
        assertThat(failedSnapshots).hasSize(2);
        assertThat(failedSnapshots).allSatisfy(snapshot -> {
            assertThat(snapshot.phase()).isEqualTo("BEFORE");
            assertThat(snapshot.roster().players()).isNotEmpty();
        });

        // 成功路径有四份快照
        createRoster(teamC, season, List.of());
        var ok = transferService.createApplication(new CreateTransferRequest(
                season.getId(), teamA.getId(), teamC.getId(), EFFECTIVE_DATE,
                List.of(item(a1, TransferDirection.FROM_TO)), "TA-" + UUID.randomUUID()));
        confirmBoth(ok.applicationNo(), teamA, teamC);
        transferService.execute(ok.applicationNo());
        var okSnapshots = transferService.getSnapshots(ok.applicationNo());
        assertThat(okSnapshots).hasSize(4);
        assertThat(okSnapshots.stream().filter(s -> s.phase().equals("BEFORE"))).hasSize(2);
        assertThat(okSnapshots.stream().filter(s -> s.phase().equals("AFTER"))).hasSize(2);

        var teamCSnapshot = okSnapshots.stream()
                .filter(s -> s.teamId().equals(teamC.getId()) && s.phase().equals("AFTER"))
                .findFirst().orElseThrow();
        assertThat(teamCSnapshot.size()).isEqualTo(1);
        assertThat(teamCSnapshot.roster().players())
                .extracting("code").containsExactly("A1");
    }

    @Test
    void playerTimelineShowsRegistrationTransferOutAndTransferInOrder() {
        setupStandardFixture(10, 10, 0);
        Player p1 = mkPlayer("P1");
        Player p2 = mkPlayer("P2");
        createRoster(teamA, season, List.of(p1));
        createRoster(teamB, season, List.of(p2));

        var created = transferService.createApplication(
                request(teamA, teamB, List.of(item(p1, TransferDirection.FROM_TO))));
        confirmBoth(created.applicationNo(), teamA, teamB);
        transferService.execute(created.applicationNo());

        List<AffiliationEventResponse> timeline =
                transferService.getPlayerTimeline(season.getId(), p1.getId());
        assertThat(timeline).hasSize(3);
        assertThat(timeline.stream().map(AffiliationEventResponse::eventType).toList())
                .containsExactly(
                        AffiliationEventType.REGISTERED.name(),
                        AffiliationEventType.TRANSFER_OUT.name(),
                        AffiliationEventType.TRANSFER_IN.name());
        assertThat(timeline.get(0).teamCode()).isEqualTo("A");
        assertThat(timeline.get(1).teamCode()).isEqualTo("A");
        assertThat(timeline.get(2).teamCode()).isEqualTo("B");
        assertThat(timeline.get(2).applicationNo()).isEqualTo(created.applicationNo());

        // 再次转会不重复回填 REGISTERED
        createRoster(teamC, season, List.of());
        var second = transferService.createApplication(new CreateTransferRequest(
                season.getId(), teamB.getId(), teamC.getId(), EFFECTIVE_DATE,
                List.of(item(p1, TransferDirection.FROM_TO)), "TA-" + UUID.randomUUID()));
        confirmBoth(second.applicationNo(), teamB, teamC);
        transferService.execute(second.applicationNo());
        List<AffiliationEventResponse> longer =
                transferService.getPlayerTimeline(season.getId(), p1.getId());
        assertThat(longer).hasSize(5);
        assertThat(longer.stream().filter(e -> e.eventType().equals("REGISTERED"))).hasSize(1);
    }

    // ------------------------------------------------------------------
    // 并发：多个转会争抢同一球员，最多一个成功
    // ------------------------------------------------------------------

    @Test
    void concurrentTransfersContendingSamePlayerAllowAtMostOneSuccess() throws Exception {
        setupStandardFixture(10, 10, 0);
        Player contended = mkPlayer("STAR");
        Player by1 = mkPlayer("BY1");
        Player by2 = mkPlayer("BY2");
        Player cz1 = mkPlayer("CZ1");
        createRoster(teamA, season, List.of(contended));
        createRoster(teamB, season, List.of(by1, by2));
        createRoster(teamC, season, List.of(cz1));

        // 两笔申请都要把 contended 从 A 队买走：A→B 与 A→C
        var toB = transferService.createApplication(
                request(teamA, teamB, List.of(item(contended, TransferDirection.FROM_TO))));
        var toC = transferService.createApplication(
                request(teamA, teamC, List.of(item(contended, TransferDirection.FROM_TO))));
        confirmBoth(toB.applicationNo(), teamA, teamB);
        confirmBoth(toC.applicationNo(), teamA, teamC);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> applicationNos = List.of(toB.applicationNo(), toC.applicationNo());

        List<Future<Outcome>> futures = new ArrayList<>();
        for (String applicationNo : applicationNos) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    TransferExecutionResponse response = transferService.execute(applicationNo);
                    return new Outcome(applicationNo, response.status(), null);
                } catch (Exception ex) {
                    return new Outcome(applicationNo, "EXCEPTION", ex.getClass().getSimpleName());
                }
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        long executed = outcomes.stream().filter(o -> o.status().equals("EXECUTED")).count();
        assertThat(executed).as("并发争抢同一球员最多一笔成功，实际结果 %s", outcomes).isEqualTo(1);
        long failedOrException = outcomes.stream()
                .filter(o -> o.status().equals("FAILED") || o.status().equals("EXCEPTION"))
                .count();
        assertThat(failedOrException).isEqualTo(1);

        // 球员最终只注册在一支球队
        List<String> ownerCodes = tx.execute(status -> entryRepository
                .findRegistrations(season.getId(), List.of(contended.getId())).stream()
                .map(entry -> entry.getRoster().getTeam().getCode())
                .collect(Collectors.toList()));
        assertThat(ownerCodes).hasSize(1);
        assertThat(ownerCodes.get(0)).isIn("B", "C");
    }

    private record Outcome(String applicationNo, String status, String error) {
    }
}
