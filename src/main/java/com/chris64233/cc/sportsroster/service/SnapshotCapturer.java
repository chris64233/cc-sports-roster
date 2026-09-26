package com.chris64233.cc.sportsroster.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.chris64233.cc.sportsroster.api.RosterResponse;
import com.chris64233.cc.sportsroster.domain.Roster;
import com.chris64233.cc.sportsroster.domain.RosterSnapshot;
import com.chris64233.cc.sportsroster.domain.SnapshotPhase;
import com.chris64233.cc.sportsroster.domain.Team;
import com.chris64233.cc.sportsroster.domain.TransferApplication;
import com.chris64233.cc.sportsroster.repo.RosterEntryRepository;
import com.chris64233.cc.sportsroster.repo.RosterSnapshotRepository;

/**
 * 拍摄并持久化名单快照：名单版本、人数统计与完整球员清单（JSON）。
 */
@Component
public class SnapshotCapturer {

    private final RosterEntryRepository entryRepository;
    private final RosterSnapshotRepository snapshotRepository;
    private final RosterResponseAssembler assembler;

    public SnapshotCapturer(RosterEntryRepository entryRepository,
                            RosterSnapshotRepository snapshotRepository,
                            RosterResponseAssembler assembler) {
        this.entryRepository = entryRepository;
        this.snapshotRepository = snapshotRepository;
        this.assembler = assembler;
    }

    public RosterSnapshot capture(TransferApplication application, Team team, Roster roster,
                                  SnapshotPhase phase, LocalDateTime now) {
        RosterResponse rosterResponse = assembler.assemble(
                roster, entryRepository.findByRosterIdWithPlayer(roster.getId()));
        RosterSnapshot snapshot = new RosterSnapshot(
                application, team, phase,
                rosterResponse.version(),
                rosterResponse.size(),
                rosterResponse.foreignCount(),
                rosterResponse.homegrownCount(),
                JsonSupport.write(rosterResponse),
                now);
        return snapshotRepository.save(snapshot);
    }
}
