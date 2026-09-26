package com.chris64233.cc.sportsroster.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.sportsroster.domain.TransferDecisionEvent;

public interface TransferDecisionEventRepository extends JpaRepository<TransferDecisionEvent, Long> {

    Optional<TransferDecisionEvent> findByEventKey(String eventKey);

    List<TransferDecisionEvent> findByApplicationIdOrderByIdAsc(Long applicationId);

    Optional<TransferDecisionEvent> findByApplicationIdAndTeamId(Long applicationId, Long teamId);
}
