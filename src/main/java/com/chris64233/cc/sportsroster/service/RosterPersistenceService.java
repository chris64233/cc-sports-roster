package com.chris64233.cc.sportsroster.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.sportsroster.api.RosterResponse;
import com.chris64233.cc.sportsroster.api.Violation;
import com.chris64233.cc.sportsroster.api.ViolationResponse;
import com.chris64233.cc.sportsroster.domain.IdempotentRequest;
import com.chris64233.cc.sportsroster.domain.Player;
import com.chris64233.cc.sportsroster.domain.Roster;
import com.chris64233.cc.sportsroster.domain.RosterEntry;
import com.chris64233.cc.sportsroster.domain.Season;
import com.chris64233.cc.sportsroster.domain.Team;
import com.chris64233.cc.sportsroster.exception.IdempotencyConflictException;
import com.chris64233.cc.sportsroster.exception.RegistrationCommitConflictException;
import com.chris64233.cc.sportsroster.exception.StaleRosterVersionException;
import com.chris64233.cc.sportsroster.repo.IdempotentRequestRepository;
import com.chris64233.cc.sportsroster.repo.RosterEntryRepository;
import com.chris64233.cc.sportsroster.repo.RosterRepository;

/**
 * 单次原子提交：悲观锁定本队名单行 → 版本校验 → 全部规则校验 →
 * 一次性释放移除球员并占用新增球员 → 记录幂等结果。任何失败均随事务整体回滚。
 */
@Service
public class RosterPersistenceService {

    public sealed interface CommitResult {

        record Success(RosterResponse roster, boolean versionBumped) implements CommitResult {
        }

        record Invalid(ViolationResponse violations) implements CommitResult {
        }

        record Replayed(int statusCode, String responseJson) implements CommitResult {
        }

        record IdempotencyClash(String message) implements CommitResult {
        }
    }

    private final RosterRepository rosterRepository;
    private final RosterEntryRepository entryRepository;
    private final IdempotentRequestRepository idempotentRequestRepository;
    private final RosterValidator validator;
    private final RosterResponseAssembler assembler;
    private final IdempotencySupport idempotencySupport;
    private final EntityManager entityManager;
    private final Clock clock;

    public RosterPersistenceService(RosterRepository rosterRepository,
                                    RosterEntryRepository entryRepository,
                                    IdempotentRequestRepository idempotentRequestRepository,
                                    RosterValidator validator,
                                    RosterResponseAssembler assembler,
                                    IdempotencySupport idempotencySupport,
                                    EntityManager entityManager,
                                    Clock clock) {
        this.rosterRepository = rosterRepository;
        this.entryRepository = entryRepository;
        this.idempotentRequestRepository = idempotencyRepository;
        this.validator = validator;
        this.assembler = assembler;
        this.idempotencySupport = idempotencySupport;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommitResult commit(Team team, Season season, Long expectedVersion,
                               LocalDate submittedOn, List<Long> playerIds,
                               String idempotencyKey, String contentHash) {
        IdempotentRequest prior = idempotencyKey == null
                ? null
                : idempotentRequestRepository.findById(idempotencyKey).orElse(null);
        if (prior != null) {
            return replayOrClash(prior, team.getId(), season.getId(), contentHash);
        }

        Roster roster = rosterRepository
                .findByTeamIdAndSeasonIdForUpdate(team.getId(), season.getId())
                .orElse(null);
        verifyVersion(roster, expectedVersion);

        RosterValidator.ValidatedRoster validation =
                validator.validate(season, roster, submittedOn, playerIds);
        if (!validation.valid()) {
            ViolationResponse body = ViolationResponse.of(validation.violations());
            storeIdempotentResult(idempotencyKey, team, season, contentHash,
                    422, idempotencySupport.write(body));
            return new CommitResult.Invalid(body);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        boolean versionBumped = replaceEntries(roster, team, season, validation.players(), now);
        entityManager.flush();

        Roster managed = rosterRepository.findByTeamIdAndSeasonId(team.getId(), season.getId()).orElseThrow();
        List<RosterEntry> updatedEntries = entryRepository.findByRosterIdWithPlayer(managed.getId());
        RosterResponse response = assembler.assemble(managed, updatedEntries);
        storeIdempotentResult(idempotencyKey, team, season, contentHash,
                200, idempotencySupport.write(response));
        return new CommitResult.Success(response, versionBumped);
    }

    /**
     * 用候选球员集合整体替换当前名单：先删除被移除的条目（释放球员），
     * 再插入新增条目（占用球员）；保留条目的注册时间不变。
     *
     * @return 是否产生了名单内容变更（影响版本递增）
     */
    private boolean replaceEntries(Roster roster, Team team, Season season,
                                   List<Player> requestedPlayers, LocalDateTime now) {
        List<RosterEntry> existing = roster == null ? List.of()
                : entryRepository.findByRosterId(roster.getId());
        Map<Long, RosterEntry> existingByPlayer = new java.util.HashMap<>();
        for (RosterEntry entry : existing) {
            existingByPlayer.put(entry.getPlayer().getId(), entry);
        }

        List<Long> deleteIds = existing.stream()
                .map(RosterEntry::getPlayer)
                .map(Player::getId)
                .filter(playerId -> requestedPlayers.stream().noneMatch(p -> Objects.equals(p.getId(), playerId)))
                .map(playerId -> existingByPlayer.get(playerId).getId())
                .toList();
        if (!deleteIds.isEmpty()) {
            entryRepository.deleteAllByIdIn(deleteIds);
        }

        Roster managedRoster = roster;
        if (managedRoster == null) {
            managedRoster = rosterRepository.saveAndFlush(new Roster(team, season, now));
        }

        boolean changed = !deleteIds.isEmpty();
        for (Player player : requestedPlayers) {
            if (!existingByPlayer.containsKey(player.getId())) {
                persistEntry(new RosterEntry(managedRoster, season, player, now));
                changed = true;
            }
        }

        if (changed) {
            managedRoster.setSubmittedAt(now);
        }
        return changed;
    }

    private void persistEntry(RosterEntry entry) {
        try {
            entityManager.persist(entry);
            entityManager.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new RegistrationCommitConflictException();
        }
    }

    private void verifyVersion(Roster roster, Long expectedVersion) {
        if (roster == null) {
            if (expectedVersion != null) {
                throw new StaleRosterVersionException("该球队在本赛季尚无名单，expectedVersion 必须为空");
            }
            return;
        }
        if (expectedVersion == null || roster.getVersion() != expectedVersion) {
            throw new StaleRosterVersionException(roster.getVersion());
        }
    }

    private CommitResult replayOrClash(IdempotentRequest prior, Long teamId, Long seasonId,
                                       String contentHash) {
        boolean sameScope = Objects.equals(prior.getTeamId(), teamId)
                && Objects.equals(prior.getSeasonId(), seasonId);
        boolean sameContent = Objects.equals(prior.getContentHash(), contentHash);
        if (!sameScope || !sameContent) {
            String message = sameScope
                    ? "相同幂等键提交的名单内容不同"
                    : "相同幂等键已用于其他球队或赛季";
            return new CommitResult.IdempotencyClash(message);
        }
        return new CommitResult.Replayed(prior.getStatusCode(), prior.getResponseJson());
    }

    private void storeIdempotentResult(String idempotencyKey, Team team, Season season,
                                       String contentHash, int statusCode, String responseJson) {
        if (idempotencyKey == null) {
            return;
        }
        try {
            idempotentRequestRepository.saveAndFlush(new IdempotentRequest(
                    idempotencyKey, team.getId(), season.getId(), contentHash,
                    statusCode, responseJson, LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException ex) {
            IdempotentRequest winner = idempotentRequestRepository.findById(idempotencyKey).orElseThrow();
            CommitResult replayed = replayOrClash(winner, team.getId(), season.getId(), contentHash);
            if (replayed instanceof CommitResult.Replayed replay) {
                throw new IdempotentReplayException(replay.statusCode(), replay.responseJson());
            }
            throw new IdempotencyConflictException(
                    replayed instanceof CommitResult.IdempotencyClash clash
                            ? clash.message()
                            : "相同幂等键提交的名单内容不同");
        }
    }
}
