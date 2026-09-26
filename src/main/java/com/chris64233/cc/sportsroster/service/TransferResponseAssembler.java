package com.chris64233.cc.sportsroster.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.chris64233.cc.sportsroster.api.AffiliationEventResponse;
import com.chris64233.cc.sportsroster.api.RosterResponse;
import com.chris64233.cc.sportsroster.api.RosterSnapshotResponse;
import com.chris64233.cc.sportsroster.api.TransferDecisionResponse;
import com.chris64233.cc.sportsroster.api.TransferProgressResponse;
import com.chris64233.cc.sportsroster.api.ViolationResponse;
import com.chris64233.cc.sportsroster.domain.PlayerAffiliationEvent;
import com.chris64233.cc.sportsroster.domain.RosterSnapshot;
import com.chris64233.cc.sportsroster.domain.TransferApplication;
import com.chris64233.cc.sportsroster.domain.TransferDecision;
import com.chris64233.cc.sportsroster.domain.TransferDecisionEvent;
import com.chris64233.cc.sportsroster.domain.TransferItem;

@Component
public class TransferResponseAssembler {

    public TransferProgressResponse assembleProgress(TransferApplication application,
                                                      List<TransferItem> items,
                                                      List<TransferDecisionEvent> decisions,
                                                      boolean replayed) {
        List<TransferProgressResponse.TransferItemView> itemViews = items.stream()
                .map(item -> new TransferProgressResponse.TransferItemView(
                        item.getPlayer().getId(),
                        item.getPlayer().getCode(),
                        item.getPlayer().getName(),
                        item.getDirection().name()))
                .toList();

        List<TransferProgressResponse.DecisionView> decisionViews = decisions.stream()
                .map(event -> new TransferProgressResponse.DecisionView(
                        event.getTeam().getId(),
                        event.getTeam().getCode(),
                        event.getDecision().name(),
                        event.getEventKey(),
                        event.getCreatedAt()))
                .toList();

        boolean fromConfirmed = decisions.stream()
                .anyMatch(event -> event.getTeam().getId().equals(application.getFromTeam().getId())
                        && event.getDecision() == TransferDecision.CONFIRM);
        boolean toConfirmed = decisions.stream()
                .anyMatch(event -> event.getTeam().getId().equals(application.getToTeam().getId())
                        && event.getDecision() == TransferDecision.CONFIRM);

        ViolationResponse failureViolations = null;
        if (application.getFailureDetails() != null) {
            failureViolations = JsonSupport.read(application.getFailureDetails(), ViolationResponse.class);
        }

        return new TransferProgressResponse(
                application.getApplicationNo(),
                application.getSeason().getId(),
                application.getSeason().getCode(),
                application.getFromTeam().getId(),
                application.getFromTeam().getCode(),
                application.getToTeam().getId(),
                application.getToTeam().getCode(),
                application.getEffectiveDate(),
                application.getStatus().name(),
                application.getFromRosterVersion(),
                application.getToRosterVersion(),
                fromConfirmed,
                toConfirmed,
                itemViews,
                decisionViews,
                failureViolations,
                application.getCreatedAt(),
                application.getDecidedAt(),
                application.getExecutedAt(),
                replayed);
    }

    public TransferDecisionResponse assembleDecision(TransferApplication application,
                                                      TransferDecisionEvent event,
                                                      boolean replayed) {
        return new TransferDecisionResponse(
                application.getApplicationNo(),
                event.getTeam().getId(),
                event.getTeam().getCode(),
                event.getDecision().name(),
                event.getEventKey(),
                application.getStatus().name(),
                event.getCreatedAt(),
                replayed);
    }

    public RosterSnapshotResponse assembleSnapshot(RosterSnapshot snapshot) {
        RosterResponse roster = JsonSupport.read(snapshot.getSnapshotJson(), RosterResponse.class);
        return new RosterSnapshotResponse(
                snapshot.getApplication().getApplicationNo(),
                snapshot.getTeam().getId(),
                snapshot.getTeam().getCode(),
                snapshot.getPhase().name(),
                snapshot.getRosterVersion(),
                snapshot.getSize(),
                snapshot.getForeignCount(),
                snapshot.getHomegrownCount(),
                roster,
                snapshot.getCapturedAt());
    }

    public AffiliationEventResponse assembleAffiliation(PlayerAffiliationEvent event) {
        return new AffiliationEventResponse(
                event.getPlayer().getId(),
                event.getPlayer().getCode(),
                event.getSeason().getId(),
                event.getSeason().getCode(),
                event.getEventType().name(),
                event.getTeam().getId(),
                event.getTeam().getCode(),
                event.getApplication() == null ? null : event.getApplication().getApplicationNo(),
                event.getEffectiveDate(),
                event.getOccurredAt());
    }
}
