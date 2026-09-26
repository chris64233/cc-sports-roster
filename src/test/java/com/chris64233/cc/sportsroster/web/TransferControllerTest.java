package com.chris64233.cc.sportsroster.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.chris64233.cc.sportsroster.domain.Player;
import com.chris64233.cc.sportsroster.domain.Roster;
import com.chris64233.cc.sportsroster.domain.RosterEntry;
import com.chris64233.cc.sportsroster.domain.Season;
import com.chris64233.cc.sportsroster.domain.Team;
import com.chris64233.cc.sportsroster.repo.PlayerRepository;
import com.chris64233.cc.sportsroster.repo.RosterEntryRepository;
import com.chris64233.cc.sportsroster.repo.RosterRepository;
import com.chris64233.cc.sportsroster.repo.SeasonRepository;
import com.chris64233.cc.sportsroster.repo.TeamRepository;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;
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
    private JdbcTemplate jdbc;

    private Season season;
    private Team teamA;
    private Team teamB;
    private Player p1;
    private Player p2;

    @BeforeEach
    void setUp() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : List.of(
                "player_affiliation_event", "roster_snapshot", "transfer_decision_event",
                "transfer_item", "transfer_application", "roster_entry", "roster",
                "player", "team", "season", "idempotent_request")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        season = seasonRepository.save(new Season("S2026", "2026 赛季",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 31), 10, 10, 0, "CN"));
        teamA = teamRepository.save(new Team("AAA", "甲队"));
        teamB = teamRepository.save(new Team("BBB", "乙队"));
        p1 = playerRepository.save(new Player("P1", "甲队球员", LocalDate.of(2000, 1, 1),
                "CN", true, null));
        p2 = playerRepository.save(new Player("P2", "乙队球员", LocalDate.of(2000, 1, 1),
                "CN", true, null));
        LocalDateTime registeredAt = LocalDateTime.of(2026, 2, 1, 10, 0);
        Roster ra = rosterRepository.save(new Roster(teamA, season, registeredAt));
        entryRepository.save(new RosterEntry(ra, season, p1, registeredAt));
        Roster rb = rosterRepository.save(new Roster(teamB, season, registeredAt));
        entryRepository.save(new RosterEntry(rb, season, p2, registeredAt));
    }

    private String createBody(String applicationNo) {
        return """
                {
                  "seasonId": %d,
                  "fromTeamId": %d,
                  "toTeamId": %d,
                  "effectiveDate": "2026-06-15",
                  "applicationNo": "%s",
                  "items": [{"playerId": %d, "direction": "FROM_TO"}]
                }""".formatted(season.getId(), teamA.getId(), teamB.getId(),
                applicationNo, p1.getId());
    }

    private String decisionBody(Long teamId, String decision, String eventKey) {
        return """
                {"teamId": %d, "decision": "%s", "eventKey": "%s"}"""
                .formatted(teamId, decision, eventKey);
    }

    @Test
    void fullTransferFlowOverHttpSucceeds() throws Exception {
        // 创建：201
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("TA-HTTP-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.items", hasSize(1)));

        // 相同申请号重放：200 + replayed
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("TA-HTTP-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        // 双方确认
        mockMvc.perform(post("/api/transfers/TA-HTTP-1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody(teamA.getId(), "CONFIRM", "EV-A-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("AWAITING_CONFIRMATION"));
        mockMvc.perform(post("/api/transfers/TA-HTTP-1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody(teamB.getId(), "CONFIRM", "EV-B-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("CONFIRMED"));

        // 决定事件号重放
        mockMvc.perform(post("/api/transfers/TA-HTTP-1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody(teamA.getId(), "CONFIRM", "EV-A-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        // 执行：200 EXECUTED
        mockMvc.perform(post("/api/transfers/TA-HTTP-1/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.executed").value(true))
                .andExpect(jsonPath("$.snapshots", hasSize(4)));

        // 进度
        mockMvc.perform(get("/api/transfers/TA-HTTP-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.fromTeamConfirmed").value(true))
                .andExpect(jsonPath("$.toTeamConfirmed").value(true));

        // 快照
        mockMvc.perform(get("/api/transfers/TA-HTTP-1/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].phase").value("BEFORE"))
                .andExpect(jsonPath("$[3].phase").value("AFTER"));

        // 时间线
        mockMvc.perform(get("/api/transfers/seasons/{seasonId}/players/{playerId}/timeline",
                        season.getId(), p1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].eventType").value("REGISTERED"))
                .andExpect(jsonPath("$[1].eventType").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$[2].eventType").value("TRANSFER_IN"))
                .andExpect(jsonPath("$[2].teamCode").value("BBB"));
    }

    @Test
    void ruleViolationReturns422WithFailureDetails() throws Exception {
        // 乙队名单填满到上限（已有 1 人，再补 9 人），甲队球员转入必超员
        Roster rb = rosterRepository.findByTeamIdAndSeasonId(teamB.getId(), season.getId())
                .orElseThrow();
        for (int i = 3; i <= 11; i++) {
            Player extra = playerRepository.save(new Player("P" + i, "球员",
                    LocalDate.of(2000, 1, 1), "CN", true, null));
            entryRepository.save(new RosterEntry(rb, season, extra,
                    LocalDateTime.of(2026, 2, 1, 10, 0)));
        }

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("TA-HTTP-FAIL")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/transfers/TA-HTTP-FAIL/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody(teamA.getId(), "CONFIRM", "EV-FA-1")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/transfers/TA-HTTP-FAIL/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody(teamB.getId(), "CONFIRM", "EV-FB-1")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transfers/TA-HTTP-FAIL/execute"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureViolations.valid").value(false))
                .andExpect(jsonPath("$.failureViolations.violations[0].code")
                        .value("ROSTER_SIZE_EXCEEDED"))
                .andExpect(jsonPath("$.snapshots", hasSize(2)));
    }

    @Test
    void rejectsRequestsFromNonPartyAndUnknownApplication() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("TA-HTTP-3")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/transfers/TA-HTTP-3/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody(999999L, "CONFIRM", "EV-X-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("当事方")));

        mockMvc.perform(get("/api/transfers/NO-SUCH-APPLICATION"))
                .andExpect(status().isNotFound());
    }

    @Test
    void outsideWindowApplicationFailsAtExecutionWith422() throws Exception {
        String body = createBody("TA-HTTP-WINDOW").replace("2026-06-15", "2026-12-15");
        // 创建允许（只冻结版本）
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/transfers/TA-HTTP-WINDOW/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody(teamA.getId(), "CONFIRM", "EV-WA-1")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/transfers/TA-HTTP-WINDOW/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody(teamB.getId(), "CONFIRM", "EV-WB-1")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transfers/TA-HTTP-WINDOW/execute"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureViolations.violations[0].code")
                        .value("TRANSFER_WINDOW_CLOSED"));
    }
}
