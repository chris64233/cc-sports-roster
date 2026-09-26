package com.chris64233.cc.sportsroster.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.sportsroster.domain.RosterSnapshot;
import com.chris64233.cc.sportsroster.domain.SnapshotPhase;

public interface RosterSnapshotRepository extends JpaRepository<RosterSnapshot, Long> {

    List<RosterSnapshot> findByApplicationIdOrderByTeamIdAscPhaseAsc(Long applicationId);

    List<RosterSnapshot> findByApplicationIdAndTeamIdOrderByPhaseAsc(Long applicationId, Long teamId);

    boolean existsByApplicationIdAndTeamIdAndPhase(Long applicationId, Long teamId, SnapshotPhase phase);
}
