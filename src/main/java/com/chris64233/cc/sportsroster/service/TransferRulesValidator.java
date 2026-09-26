package com.chris64233.cc.sportsroster.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chris64233.cc.sportsroster.api.Violation;
import com.chris64233.cc.sportsroster.domain.Player;
import com.chris64233.cc.sportsroster.domain.Season;
import com.chris64233.cc.sportsroster.domain.Team;

/**
 * 转会规则校验：对交换完成后的完整候选名单判断总人数、外籍上限、本土培养下限，
 * 不检查任何中间状态。窗口与禁赛属于执行前置条件，由转会服务单独校验。
 */
@Component
public class TransferRulesValidator {

    public List<Violation> validateCompletedRoster(Season season, Team team,
                                                   LocalDate effectiveDate,
                                                   List<Player> resultingPlayers) {
        List<Violation> violations = new ArrayList<>();

        int totalCount = resultingPlayers.size();
        if (totalCount > season.getRosterSizeMax()) {
            violations.add(Violation.of("ROSTER_SIZE_EXCEEDED",
                    "球队 " + team.getCode() + " 交换后名单人数 " + totalCount
                            + " 超过上限 " + season.getRosterSizeMax(),
                    Map.of("teamId", team.getId(), "teamCode", team.getCode(),
                            "actual", totalCount, "limit", season.getRosterSizeMax(),
                            "effectiveDate", effectiveDate.toString())));
        }

        long foreignCount = resultingPlayers.stream()
                .filter(player -> !season.getDomesticNationality().equalsIgnoreCase(player.getNationality()))
                .count();
        if (foreignCount > season.getForeignPlayerMax()) {
            violations.add(Violation.of("FOREIGN_PLAYER_LIMIT_EXCEEDED",
                    "球队 " + team.getCode() + " 交换后外籍球员 " + foreignCount
                            + " 人，超过上限 " + season.getForeignPlayerMax(),
                    Map.of("teamId", team.getId(), "teamCode", team.getCode(),
                            "actual", foreignCount, "limit", season.getForeignPlayerMax(),
                            "effectiveDate", effectiveDate.toString())));
        }

        long homegrownCount = resultingPlayers.stream().filter(Player::isHomegrown).count();
        if (homegrownCount < season.getHomegrownMin()) {
            violations.add(Violation.of("HOMEGROWN_MINIMUM_NOT_MET",
                    "球队 " + team.getCode() + " 交换后本土培养球员 " + homegrownCount
                            + " 人，低于下限 " + season.getHomegrownMin(),
                    Map.of("teamId", team.getId(), "teamCode", team.getCode(),
                            "actual", homegrownCount, "minimum", season.getHomegrownMin(),
                            "effectiveDate", effectiveDate.toString())));
        }

        return violations;
    }
}
