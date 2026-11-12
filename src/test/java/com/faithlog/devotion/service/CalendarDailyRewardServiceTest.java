package com.faithlog.devotion.service;

import static org.assertj.core.api.Assertions.*;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.devotion.domain.type.DailyMission;
import com.faithlog.devotion.service.command.UpdateDailyDevotionCommand;
import com.faithlog.devotion.service.command.UpdateWeeklyDevotionCommand;
import com.faithlog.devotion.service.command.DevotionDailyCheckCommand;
import com.faithlog.devotion.infrastructure.repository.CalendarDailyRewardRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
@Import(CalendarDailyRewardServiceTest.Time.class)
class CalendarDailyRewardServiceTest {
 @TestConfiguration static class Time {
  @Bean @Primary Clock missionTestClock() {return Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"),ZoneOffset.UTC);}
 }
 @Autowired CalendarDailyRewardService rewards;
 @Autowired DailyDevotionCommandService daily;
 @Autowired WeeklyDevotionCommandService weekly;
 @Autowired UserRepository users;
 @Autowired CampusService campuses;
 @Autowired CalendarDailyRewardRepository rows;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 private static final LocalDate TODAY=LocalDate.of(2026,11,11);
 private record Scope(Long campus,Long user) {}
 private Scope scope() {
  var user=users.saveAndFlush(User.create("미션",UUID.randomUUID()+"@example.com","test-only-password"));
  ReflectionTestUtils.setField(user,"role",UserRole.MANAGER);
  users.saveAndFlush(user);
  var campus=campuses.createCampus(new CreateCampusCommand(user.id(),"미션캠","분당","테스트"));
  return new Scope(campus.campusId(),user.id());
 }
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.faithlog.points.PointTransactionRepository pointLedger;
 @Test void loads_all_daily_claim_keys_in_one_query() {
  var s=scope();
  org.mockito.Mockito.clearInvocations(pointLedger);
  rewards.getToday(s.campus,s.user);
  org.mockito.Mockito.verify(pointLedger).findBusinessKeysByUserIdAndBusinessKeyIn(org.mockito.ArgumentMatchers.eq(s.user),org.mockito.ArgumentMatchers.anyCollection());
  org.mockito.Mockito.verify(pointLedger,org.mockito.Mockito.never()).findByUserIdAndBusinessKey(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
 }
 @Test void only_successful_calendar_save_opens_rewards_and_unchecking_closes_unclaimed_rewards() {
  var s=scope();
  assertThat(rewards.getToday(s.campus,s.user).recordDate()).isEqualTo(TODAY);
  assertThat(rewards.getToday(s.campus,s.user).missions()).allMatch(m -> m.status().equals("INCOMPLETE"));
  assertThatThrownBy(() -> rewards.claim(s.campus,s.user,DailyMission.PRAYER)).isInstanceOf(RuntimeException.class);
  daily.updateDailyCheck(new UpdateDailyDevotionCommand(s.campus,s.user,TODAY,true,true,false));
  assertThat(rewards.getToday(s.campus,s.user).missions().stream().map(m -> m.status())).containsExactly("READY","READY","INCOMPLETE");
  daily.updateDailyCheck(new UpdateDailyDevotionCommand(s.campus,s.user,TODAY,true,false,false));
  assertThatThrownBy(() -> rewards.claim(s.campus,s.user,DailyMission.PRAYER)).isInstanceOf(RuntimeException.class);
  assertThat(rewards.claim(s.campus,s.user,DailyMission.QUIET_TIME).totalAwardedPoints()).isEqualTo(30);
  daily.updateDailyCheck(new UpdateDailyDevotionCommand(s.campus,s.user,TODAY,false,false,false));
  daily.updateDailyCheck(new UpdateDailyDevotionCommand(s.campus,s.user,TODAY,true,true,true));
  assertThat(rewards.claim(s.campus,s.user,DailyMission.QUIET_TIME).totalAwardedPoints()).isEqualTo(30);
 }
 @Test void weekly_save_and_past_or_future_calendar_dates_do_not_create_todays_eligibility() {
  var s=scope();
  weekly.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(s.campus,s.user,LocalDate.of(2026,11,9),List.of(new DevotionDailyCheckCommand(TODAY,true,true,true)),0,false));
  daily.updateDailyCheck(new UpdateDailyDevotionCommand(s.campus,s.user,TODAY.minusDays(1),true,true,true));
  daily.updateDailyCheck(new UpdateDailyDevotionCommand(s.campus,s.user,TODAY.plusDays(1),true,true,true));
  assertThat(rewards.getToday(s.campus,s.user).missions()).allMatch(m -> m.status().equals("INCOMPLETE"));
 }
 @Test void parallel_claims_across_devices_award_thirty_points_once() throws Exception {
  var s=scope();
  daily.updateDailyCheck(new UpdateDailyDevotionCommand(s.campus,s.user,TODAY,true,true,true));
  var pool=Executors.newFixedThreadPool(2);
  try {
   var start=new CountDownLatch(1);
   Callable<Long> task=() -> {start.await(); return rewards.claim(s.campus,s.user,DailyMission.BIBLE).totalAwardedPoints();};
   var a=pool.submit(task); var b=pool.submit(task); start.countDown();
   assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(30);
   assertThat(b.get(10,TimeUnit.SECONDS)).isEqualTo(30);
   assertThat(rows.totalAwarded(s.campus,s.user)).isEqualTo(30);
  } finally {pool.shutdownNow();}
 }
 @Test void failed_calendar_transaction_rolls_back_eligibility_and_daily_data() {
  var s=scope();
  var transaction=new org.springframework.transaction.support.TransactionTemplate(transactions);
  assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
   daily.updateDailyCheck(new UpdateDailyDevotionCommand(s.campus,s.user,TODAY,true,true,true));
   throw new IllegalStateException("test-only rollback");
  })).isInstanceOf(IllegalStateException.class);
  assertThat(rows.findByCampusIdAndUserIdAndRecordDate(s.campus,s.user,TODAY)).isEmpty();
  assertThat(rewards.getToday(s.campus,s.user).missions()).allMatch(m -> m.status().equals("INCOMPLETE"));
 }
 @Test void other_campus_cannot_read_or_claim() {
  var a=scope(); var b=scope();
  assertThatThrownBy(() -> rewards.getToday(a.campus,b.user)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(() -> rewards.claim(a.campus,b.user,DailyMission.BIBLE)).isInstanceOf(RuntimeException.class);
 }
 @Autowired com.faithlog.points.PointWalletService wallet;
 @Test void same_daily_mission_in_two_campuses_credits_account_once(){
  var s=scope();
  var other=campuses.createCampus(new CreateCampusCommand(s.user,"다른캠퍼스","분당","QA"));
  daily.updateDailyCheck(new UpdateDailyDevotionCommand(s.campus,s.user,TODAY,true,false,false));
  daily.updateDailyCheck(new UpdateDailyDevotionCommand(other.campusId(),s.user,TODAY,true,false,false));
  rewards.claim(s.campus,s.user,DailyMission.QUIET_TIME);
  assertThat(rewards.getToday(other.campusId(),s.user).missions()).filteredOn(m -> m.mission()==DailyMission.QUIET_TIME).allMatch(m -> m.status().equals("CLAIMED"));
  rewards.claim(other.campusId(),s.user,DailyMission.QUIET_TIME);
  assertThat(wallet.wallet(s.user).balance()).isEqualTo(30);
 }

}
