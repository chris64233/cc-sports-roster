package com.chris64233.cc.sportsroster.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chris64233.cc.sportsroster.api.Violation;
import com.chris64233.cc.sportsroster.domain.Player;
import com.chris64233.cc.sportsroster.domain.Roster;
import com.chris64233.cc.sportsroster.domain.RosterEntry;
import com.chris64233.cc.sportsroster.domain.Season;
import com.chris64233.cc.sportsroster.repo.PlayerRepository;
import com.chris64233.cc.sportsroster.repo.RosterEntryRepository;

@Component
public class RosterValidator {

    private final PlayerRepository playerRepository;
    private final RosterEntryRepository entryRepository;

    public RosterValidator(PlayerRepository playerRepository, RosterEntryRepository entryRepository) {
        this.playerRepository = playerRepository;
        this.entryRepository = entryRepository;
    }

    public record ValidatedRoster(List<Player> players, List<Violation> violations) {
        public boolean valid() {
            return violations.isEmpty();
        }
    }

    /**
     * 对整份候选名单执行全部规则校验，一次性返回所有违规明细。
     *
     * @param currentRoster 当前名单（首次提交时为 null）；本队名单内的球员不算注册冲突
     */
    public ValidatedRoster validate(Season season, Roster currentRoster,
                                    LocalDate submittedOn, List<Long> requestedPlayerIds) {
        List<Violation> violations = new ArrayList<>();

        if (submittedOn.isBefore(season.getRegistrationStart())
                || submittedOn.isAfter(season.getRegistrationEnd())) {
            violations.add(Violation.of("REGISTRATION_WINDOW_CLOSED",
                    "提交日期 " + submittedOn + " 不在赛季注册窗口 "
                            + season.getRegistrationStart() + " 至 " + season.getRegistrationEnd() + " 内",
                    Map.of("submittedOn", submittedOn,
                            "windowStart", season.getRegistrationStart(),
                            "windowEnd", season.getRegistrationEnd())));
        }

        if (requestedPlayerIds.isEmpty()) {
            violations.add(Violation.of("ROSTER_EMPTY", "名单不能为空"));
            return new ValidatedRoster(List.of(), violations);
        }

        Map<Long, Long> occurrenceCount = new HashMap<>();
        for (Long playerId : requestedPlayerIds) {
            occurrenceCount.merge(playerId, 1L, Long::sum);
        }
        for (Map.Entry<Long, Long> occurrence : occurrenceCount.entrySet()) {
            if (occurrence.getValue() > 1) {
                violations.add(Violation.player("DUPLICATE_PLAYER",
                        "球员 " + occurrence.getKey() + " 在名单中重复出现 " + occurrence.getValue() + " 次",
                        occurrence.getKey()));
            }
        }

        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(requestedPlayerIds));
        List<Player> players = playerRepository.findByIdInOrderByCodeAsc(distinctIds);
        Map<Long, Player> playerById = new HashMap<>();
        for (Player player : players) {
            playerById.put(player.getId(), player);
        }
        List<Player> requestedPlayers = new ArrayList<>();
        for (Long playerId : distinctIds) {
            Player player = playerById.get(playerId);
            if (player == null) {
                violations.add(Violation.player("PLAYER_NOT_FOUND",
                        "球员 " + playerId + " 不存在", playerId));
            } else {
                requestedPlayers.add(player);
            }
        }

        for (Player player : requestedPlayers) {
            LocalDate suspensionUntil = player.getSuspensionUntil();
            if (suspensionUntil != null && !suspensionUntil.isBefore(submittedOn)) {
                violations.add(Violation.player("PLAYER_SUSPENDED",
                        "球员 " + player.getCode() + " 处于禁赛期（禁赛截止 " + suspensionUntil + "）",
                        player.getId()));
            }
        }

        int totalCount = requestedPlayerIds.size();
        if (totalCount > season.getRosterSizeMax()) {
            violations.add(Violation.of("ROSTER_SIZE_EXCEEDED",
                    "名单人数 " + totalCount + " 超过上限 " + season.getRosterSizeMax(),
                    Map.of("actual", totalCount, "limit", season.getRosterSizeMax())));
        }

        long foreignCount = requestedPlayers.stream()
                .filter(player -> !season.getDomesticNationality().equalsIgnoreCase(player.getNationality()))
                .count();
        if (foreignCount > season.getForeignPlayerMax()) {
            violations.add(Violation.of("FOREIGN_PLAYER_LIMIT_EXCEEDED",
                    "外籍球员 " + foreignCount + " 人，超过上限 " + season.getForeignPlayerMax(),
                    Map.of("actual", foreignCount, "limit", season.getForeignPlayerMax())));
        }

        long homegrownCount = requestedPlayers.stream().filter(Player::isHomegrown).count();
        if (homegrownCount < season.getHomegrownMin()) {
            violations.add(Violation.of("HOMEGROWN_MINIMUM_NOT_MET",
                    "本土培养球员 " + homegrownCount + " 人，低于下限 " + season.getHomegrownMin(),
                    Map.of("actual", homegrownCount, "minimum", season.getHomegrownMin())));
        }

        Long currentRosterId = currentRoster == null ? null : currentRoster.getId();
        for (RosterEntry entry : entryRepository.findRegistrations(season.getId(), distinctIds)) {
            if (currentRosterId == null || !entry.getRoster().getId().equals(currentRosterId)) {
                Player player = entry.getPlayer();
                violations.add(Violation.player("PLAYER_ALREADY_REGISTERED",
                        "球员 " + player.getCode() + " 已在本赛季注册给球队 "
                                + entry.getRoster().getTeam().getCode(),
                        player.getId()));
            }
        }

        return new ValidatedRoster(requestedPlayers, violations);
    }
}
