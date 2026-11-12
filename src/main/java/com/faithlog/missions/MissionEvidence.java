package com.faithlog.missions;

import java.time.*;
import java.util.List;

public record MissionEvidence(List<Week> weeks,List<Long> responseIds) {
 public MissionEvidence {weeks=List.copyOf(weeks);responseIds=List.copyOf(responseIds);}
 public boolean eligible(PeriodMission mission,MissionWindow window,Instant joinedAt) {
  if(mission.fullMembershipRequired()&&joinedAt.isAfter(MissionWindow.midnight(window.start())))return false;
  if(mission==PeriodMission.WEEKLY_VOTE)return !responseIds.isEmpty();
  if(!weeks.stream().map(Week::start).toList().containsAll(window.weeks()))return false;
  return !mission.noPenalty()||weeks.stream().noneMatch(Week::penalty);
 }
 public record Week(Long id,LocalDate start,boolean penalty) {}
}
