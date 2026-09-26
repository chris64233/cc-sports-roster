package com.chris64233.cc.sportsroster.repo;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.sportsroster.domain.Roster;

public interface RosterRepository extends JpaRepository<Roster, Long> {

    Optional<Roster> findByTeamIdAndSeasonId(Long teamId, Long seasonId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Roster r where r.team.id = :teamId and r.season.id = :seasonId")
    Optional<Roster> findByTeamIdAndSeasonIdForUpdate(@Param("teamId") Long teamId,
                                                      @Param("seasonId") Long seasonId);
}
