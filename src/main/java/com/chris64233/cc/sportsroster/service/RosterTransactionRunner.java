package com.chris64233.cc.sportsroster.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
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
import com.chris64233.cc.sportsroster.exception.RegistrationCommitConflictException;
import com.chris64233.cc.sportsroster.exception.StaleRosterVersionException;
import com.chris64233.cc.sportsroster.repo.IdempotentRequestRepository;
import com.chris64233.cc.sportsroster.repo.RosterEntryRepository;
import com.chris64233.cc.sportsroster.repo.RosterRepository;

@Component
public class RosterTransactionRunner {

    public record AttemptResult(boolean valid, Roster roster, List<RosterEntry> entries,
                                ViolationResponse violationResponse) {
    }

    private final RosterRepository rosterRepository;
    private final RosterEntryRepository entryRepository;
    private final IdempotentRequestRepository idempotentRequestRepository;
    private final RosterValidator validator;
    private final RosterResponseAssembler assembler;
    private final EntityManager entityManager;

    public RosterTransactionRunner(RosterRepository rosterRepository,
                                   RosterEntryRepository entryRepository,
                                   IdempotentRequestRepository idempotentRequestRepository,
                                   RosterValidator validator,
                                   RosterResponseAssembler assembler,
                                   EntityManager entityManager) {
        this.rosterRepository = rosterRepository;
        this.entryRepository = entryRepository;
        this.idempotentRequestRepository = idempotentRequestRepository;
        this.validator = validator;
        this.assembler = assembler;
        this.entityManager = entityManager;
    }

    @Transactional
    public AttemptResult attempt(Team team, Season season, Long expectedVersion,
                                 LocalDate submittedOn, List<Long> playerIds,
                                 String idempotencyKey, String contentHash,
                                 LocalDateTime now, String validationFailureJson) {
        Roster roster = rosterRepository.findByTeamIdAndSeasonIdForUpdate(team.getId(), season.getId())
                .orElse(null);
        if (roster != null) {
            if (expectedVersion == null || roster.getVersion() != expectedVersion) {
                throw new StaleRosterVersionException(roster.getVersion());
            }
        }

        RosterValidator.ValidatedRoster validation =
                validator.validate(season, roster, submittedOn, playerIds);
        if (!validation.valid()) {
            ViolationResponse response = ViolationResponse.invalid(validation.violations());
            saveIdempotent(idempotencyKey, team, season, contentHash, 422, validationFailureJson);
            return new AttemptResult(false, roster, List.of(), response);
        }

        LocalDateTime registeredAt = now;
        if (roster == null) {
            roster = rosterRepository.saveAndFlush(new Roster(team, season, registeredAt));
        } else {
            roster.setSubmittedAt(registeredAt);
        }

        List<RosterEntry> existing = entryRepository.findByRosterId(roster.getId());
        List<Long> retainedIds = new ArrayList<>(playerIds);
        List<Long> removedIds = existing.stream()
                .map(RosterEntry::getId)
                .filter(entryId -> true)
                .toList();
        List<Long> deleteIds = existing.stream()
                .filter(entry -> !retainedIds.contains(entry.getPlayer().getId()))
                .map(RosterEntry::getId)
                .toList();
        if (!deleteIds.isEmpty()) {
            entryRepository.deleteAllByIdIn(deleteIds);
        }

        try {
            for (Player player : validation.players()) {
                boolean alreadyOnRoster = existing.stream()
                        .anyMatch(entry -> entry.getPlayer().getId().equals(player.getId()));
                if (!alreadyOnRoster) {
                    entityManager.persist(new RosterEntry(roster, season, player, registeredAt));
                }
            }
            entityManager.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new RegistrationCommitConflictException();
        }

        List<RosterEntry> updatedEntries = entryRepository.findByRosterIdWithPlayer(roster.getId());
        RosterResponse response = assembler.assemble(roster, updatedEntries);
        String successJson;
        try {
            successJson = JsonSupport.write(response);
        } catch (JsonSupport.JsonWriteException e) {
            throw new IllegalStateException(e);
        }
        saveIdempotent(idempotencyKey, team, season, contentHash, 200, successJson);
        return new AttemptResult(true, roster, updatedEntries, null);
    }

    private void saveIdempotent(String idempotencyKey, Team team, Season season, String contentHash,
                                int statusCode, String responseJson) {
        try {
            idempotentRequestRepository.saveAndFlush(new IdempotentRequest(
                    idempotencyKey, team.getId(), season.getId(), contentHash,
                    statusCode, responseJson, LocalDateTime.now()));
        } catch (DataIntegrityViolationException ex) {
            throw new RegistrationCommitConflictException();
        }
    }
}
