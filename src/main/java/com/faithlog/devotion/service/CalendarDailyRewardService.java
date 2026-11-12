package com.faithlog.devotion.service;

import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.devotion.domain.entity.CalendarDailyReward;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.type.DailyMission;
import com.faithlog.devotion.infrastructure.repository.*;
import com.faithlog.devotion.service.command.UpdateDailyDevotionCommand;
import com.faithlog.devotion.service.result.DailyMissionResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarDailyRewardService {
 private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
 private final CampusMemberRepositoryPort members;
 private final CampusUserLookupPort users;
 private final CalendarDailyRewardRepository rewards;
 private final WeeklyDevotionRecordRepository weeks;
 private final DevotionDailyCheckRepository checks;
 private final Clock clock;
 private final com.faithlog.points.PointWalletService pointWallet;
 private final com.faithlog.points.PointTransactionRepository pointTransactions;
 public CalendarDailyRewardService(CampusMemberRepositoryPort members, CampusUserLookupPort users, CalendarDailyRewardRepository rewards, WeeklyDevotionRecordRepository weeks, DevotionDailyCheckRepository checks, Clock clock, com.faithlog.points.PointWalletService pointWallet, com.faithlog.points.PointTransactionRepository pointTransactions) {
  this.members=members; this.users=users; this.rewards=rewards; this.weeks=weeks; this.checks=checks; this.clock=clock; this.pointWallet=pointWallet;this.pointTransactions=pointTransactions;
 }
 // Serialize scope creation and claim, including a first-ever calendar save.
 public void lockMember(Long campusId,Long userId) {
  pointWallet.lockAccount(userId);
  users.findCampusUserById(userId).filter(user -> user.active()).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
  members.findByCampusIdAndUserIdForUpdate(campusId,userId).filter(member -> member.isActive()).orElseThrow(() -> new BusinessException(ErrorCode.DEVOTION_ACCESS_FORBIDDEN));
 }
 // Called only inside the calendar/day-save transaction, never by weekly save.
 public void recordCalendarSave(UpdateDailyDevotionCommand command) {
  if (!command.recordDate().equals(LocalDate.now(clock.withZone(SEOUL)))) return;
  var existing=rewards.findByCampusIdAndUserIdAndRecordDate(command.campusId(),command.requesterId(),command.recordDate());
  for (DailyMission mission:DailyMission.values()) {
   var reward=existing.stream().filter(r -> r.mission()==mission).findFirst().orElseGet(() -> new CalendarDailyReward(command.campusId(),command.requesterId(),command.recordDate(),mission));
   reward.recordSave(switch(mission) {case QUIET_TIME -> command.quietTimeChecked(); case PRAYER -> command.prayerChecked(); case BIBLE -> command.bibleReadingChecked();});
   rewards.save(reward);
  }
 }
 @Transactional
 public DailyMissionResult getToday(Long campusId,Long userId) {return today(campusId,userId,null);}
 @Transactional
 public DailyMissionResult claim(Long campusId,Long userId,DailyMission mission) {return today(campusId,userId,mission);}
 private DailyMissionResult today(Long campusId,Long userId,DailyMission claim) {
  lockMember(campusId,userId);
  Instant now=clock.instant();
  LocalDate date=now.atZone(SEOUL).toLocalDate();
  var rows=rewards.findByCampusIdAndUserIdAndRecordDate(campusId,userId,date);
  LocalDate monday=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  DevotionDailyCheck current=weeks.findByCampusIdAndUserIdAndWeekStartDate(campusId,userId,monday).flatMap(week -> checks.findByWeeklyRecordIdAndRecordDate(week.id(),date)).orElse(null);
  var claimed=EnumSet.noneOf(DailyMission.class);
  for(var mission:DailyMission.values())if(pointTransactions.findByUserIdAndBusinessKey(userId,"daily:"+date+":"+mission.name()).isPresent())claimed.add(mission);
  if (claim!=null && !claimed.contains(claim)) {
   var reward=rows.stream().filter(r -> r.mission()==claim).findFirst().orElseThrow(() -> new BusinessException(ErrorCode.DEVOTION_MISSION_NOT_READY));
   reward.claim(now,claim.checked(current));
   rewards.saveAndFlush(reward);
   pointWallet.settle(userId,30,"daily:"+date+":"+claim.name());
   claimed.add(claim);
  }
  var items=Arrays.stream(DailyMission.values()).map(mission -> {
   var reward=rows.stream().filter(r -> r.mission()==mission).findFirst().orElse(null);
   String status=claimed.contains(mission) || reward!=null && reward.claimedAt()!=null ? "CLAIMED" : reward!=null && reward.ready(mission.checked(current)) ? "READY" : "INCOMPLETE";
   return new DailyMissionResult.Item(mission,status,30);
  }).toList();
  return new DailyMissionResult(date,now,rewards.totalAwarded(campusId,userId),items);
 }
}
