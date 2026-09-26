package com.chris64233.cc.sportsroster.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.sportsroster.domain.RosterEntry;

public interface RosterEntryRepository extends JpaRepository<RosterEntry, Long> {

    @Query("select e from RosterEntry e join fetch e.player p "
            + "where e.roster.id = :rosterId order by p.code asc")
    List<RosterEntry> findByRosterIdWithPlayer(@Param("rosterId") Long rosterId);

    @Query("select e from RosterEntry e where e.roster.id = :rosterId")
    List<RosterEntry> findByRosterId(@Param("rosterId") Long rosterId);

    @Query("select e from RosterEntry e join fetch e.player p join fetch e.roster r join fetch r.team t "
            + "where e.season.id = :seasonId and e.player.id = :playerId")
    Optional<RosterEntry> findRegistration(@Param("seasonId") Long seasonId,
                                           @Param("playerId") Long playerId);

    @Query("select e from RosterEntry e join fetch e.player p join fetch e.roster r join fetch r.team t "
            + "where e.season.id = :seasonId and e.player.id in :playerIds")
    List<RosterEntry> findRegistrations(@Param("seasonId") Long seasonId,
                                        @Param("playerIds") List<Long> playerIds);

    @Modifying
    @Query("delete from RosterEntry e where e.id in :ids")
    int deleteAllByIdIn(@Param("ids") List<Long> ids);
}
