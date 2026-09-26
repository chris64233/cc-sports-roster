package com.chris64233.cc.sportsroster.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.sportsroster.domain.AffiliationEventType;
import com.chris64233.cc.sportsroster.domain.PlayerAffiliationEvent;

public interface PlayerAffiliationEventRepository extends JpaRepository<PlayerAffiliationEvent, Long> {

    @Query("select e from PlayerAffiliationEvent e join fetch e.team t "
            + "where e.player.id = :playerId and e.season.id = :seasonId order by e.effectiveDate asc, e.id asc")
    List<PlayerAffiliationEvent> findTimeline(@Param("seasonId") Long seasonId,
                                              @Param("playerId") Long playerId);

    boolean existsBySeasonIdAndPlayerIdAndEventTypeAndApplicationNull(
            Long seasonId, Long playerId, AffiliationEventType eventType);

    boolean existsByApplicationId(Long applicationId);
}
