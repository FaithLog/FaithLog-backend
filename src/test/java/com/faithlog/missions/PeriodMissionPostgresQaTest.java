package com.faithlog.missions;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.faithlog.points.PointWalletService;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.domain.entity.User;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(PeriodMissionPostgresQaTest.Time.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs(outputDir="build/generated-snippets")
@EnabledIfEnvironmentVariable(named="FAITHLOG_RUN_POSTGRES_FLYWAY_TEST",matches="true")
class PeriodMissionPostgresQaTest {
 private static final String DB="missions_qa_"+UUID.randomUUID().toString().replace("-","");
 private static final Instant NOW=Instant.parse("2026-12-07T03:00:00Z");
 @TestConfiguration static class Time {
  @Bean @Primary Clock periodMissionClock(){return Clock.fixed(NOW,ZoneOffset.UTC);}
 }
 @DynamicPropertySource static void database(DynamicPropertyRegistry properties)throws Exception {
  String base=System.getenv("FLYWAY_TEST_JDBC_URL"),user=System.getenv("FLYWAY_TEST_USERNAME"),password=System.getenv("FLYWAY_TEST_PASSWORD");
  try(var c=DriverManager.getConnection(base,user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+DB);}
  properties.add("spring.datasource.url",()->base.substring(0,base.lastIndexOf('/')+1)+DB);
  properties.add("spring.datasource.username",()->user);properties.add("spring.datasource.password",()->password);
  properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
  properties.add("spring.flyway.enabled",()->true);properties.add("spring.jpa.hibernate.ddl-auto",()->"validate");
 }
 @Autowired PeriodMissionService missions;@Autowired PointWalletService wallet;@Autowired UserRepository users;
 @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tx;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
 private record Scope(long campus,long user){}
 private Scope scope() {
  long user=users.saveAndFlush(User.create("미션 QA",UUID.randomUUID()+"@example.test","test-only")).id();
  return new Scope(campus(user),user);
 }
 private long campus(long user) {
  long campus=jdbc.queryForObject("INSERT INTO campuses(name,invite_code,is_active,created_at,updated_at) VALUES('QA',?,true,?,?) RETURNING id",Long.class,UUID.randomUUID().toString(),Timestamp.from(NOW),Timestamp.from(NOW));
  jdbc.update("INSERT INTO campus_members(campus_id,user_id,campus_role,status,joined_at,created_at,updated_at) VALUES(?,?,'MEMBER','ACTIVE',?,?,?)",campus,user,Timestamp.from(Instant.parse("2025-01-01T00:00:00Z")),Timestamp.from(NOW),Timestamp.from(NOW));
  return campus;
 }
 private long week(Scope s,LocalDate date,boolean submitted) {
  return jdbc.queryForObject("INSERT INTO weekly_devotion_records(campus_id,user_id,week_start_date,week_end_date,quiet_time_count,prayer_count,bible_reading_count,saturday_late_minutes,submitted_at,created_at,updated_at) VALUES(?,?,?,?,5,5,5,0,?,?,?) RETURNING id",Long.class,s.campus,s.user,date,date.plusDays(6),submitted?Timestamp.from(NOW.minusSeconds(60)):null,Timestamp.from(NOW),Timestamp.from(NOW));
 }
 private long vote(Scope s,Instant created,boolean selected) {
  long poll=jdbc.queryForObject("INSERT INTO polls(campus_id,title,poll_type,selection_type,charge_generation_type,starts_at,ends_at,status,created_at,updated_at) VALUES(?,'QA','CUSTOM','SINGLE','NONE',?,?,'OPEN',?,?) RETURNING id",Long.class,s.campus,Timestamp.from(NOW.minusSeconds(1000000)),Timestamp.from(NOW.plusSeconds(1000000)),Timestamp.from(NOW),Timestamp.from(NOW));
  long option=jdbc.queryForObject("INSERT INTO poll_options(poll_id,content,price_amount,sort_order) VALUES(?,'선택',0,0) RETURNING id",Long.class,poll);
  long response=jdbc.queryForObject("INSERT INTO poll_responses(poll_id,user_id,responded_at,created_at,updated_at) VALUES(?,?,?,?,?) RETURNING id",Long.class,poll,s.user,Timestamp.from(NOW.minusSeconds(30)),Timestamp.from(created),Timestamp.from(NOW));
  if(selected)jdbc.update("INSERT INTO poll_response_options(response_id,option_id) VALUES(?,?)",response,option);
  return response;
 }
 private void penalty(Scope s,long week,String category,String status) {
  long account=jdbc.queryForObject("INSERT INTO payment_accounts(campus_id,account_type,nickname,bank_name,account_number,account_holder,owner_user_id,is_active,created_at,updated_at) VALUES(?,?,'QA','test-only','test-only','QA',?,true,?,?) RETURNING id",Long.class,s.campus,category,s.user,Timestamp.from(NOW),Timestamp.from(NOW));
  String source=category.equals("PENALTY")?"DEVOTION_RECORD":"POLL_RESPONSE";
  long sourceId=category.equals("PENALTY")?week:vote(s,NOW.minusSeconds(60),true);
  jdbc.update("INSERT INTO charge_items(campus_id,user_id,payment_category,payment_account_id,bank_name_snapshot,account_number_snapshot,account_holder_snapshot,source_type,source_id,title,amount,status,paid_at,created_at,updated_at) VALUES(?,?,?,?, 'test-only','test-only','QA',?,?,'QA',100,?,?,?,?)",s.campus,s.user,category,account,source,sourceId,status,status.equals("PAID")?Timestamp.from(NOW):null,Timestamp.from(NOW),Timestamp.from(NOW));
 }
 private String missionStatus(Scope s,PeriodMission mission) {return missions.get(s.campus,s.user).missions().stream().filter(m->m.mission()==mission).findFirst().orElseThrow().status();}
 @Test void all_five_missions_use_real_sources_and_account_common_claims() {
  var s=scope();
  for(var d:MissionWindow.forMission(PeriodMission.MONTHLY_ALL_SUBMISSIONS,NOW).weeks())week(s,d,true);
  week(s,LocalDate.of(2026,12,7),true);vote(s,NOW.minusSeconds(60),true);
  assertThat(missions.get(s.campus,s.user).missions()).allMatch(m->m.status().equals("READY"));
  for(var m:PeriodMission.values())missions.claim(s.campus,s.user,m);
  assertThat(wallet.wallet(s.user).balance()).isEqualTo(1700);
  long second=campus(s.user);
  assertThat(missions.claim(second,s.user,PeriodMission.WEEKLY_SUBMISSION).missions()).allMatch(m->m.status().equals("CLAIMED"));
  assertThat(wallet.wallet(s.user).balance()).isEqualTo(1700);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM mission_claims WHERE user_id=?",Long.class,s.user)).isEqualTo(5);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM mission_claims WHERE user_id=? AND evidence_json->>'policyVersion'='period-v1' AND source_campus_id=?",Long.class,s.user,s.campus)).isEqualTo(5);
 }
 @Test void missing_rows_and_new_members_do_not_receive_period_bonus() {
  var s=scope();
  assertThat(missionStatus(s,PeriodMission.WEEKLY_NO_PENALTY)).isEqualTo("INCOMPLETE");
  assertThatThrownBy(()->missions.claim(s.campus,s.user,PeriodMission.MONTHLY_ALL_SUBMISSIONS)).isInstanceOf(RuntimeException.class);
  week(s,LocalDate.of(2026,11,30),true);
  jdbc.update("UPDATE campus_members SET joined_at=? WHERE campus_id=? AND user_id=?",Timestamp.from(Instant.parse("2026-12-01T00:00:00Z")),s.campus,s.user);
  assertThat(missionStatus(s,PeriodMission.WEEKLY_NO_PENALTY)).isEqualTo("INCOMPLETE");
  assertThat(wallet.wallet(s.user).balance()).isEqualTo(1000);
 }
 @Test void paid_and_waived_penalties_remain_penalties_and_coffee_and_meals_are_excluded() {
  for(String state:List.of("UNPAID","PAID","WAIVED")) {
   var s=scope();long w=week(s,LocalDate.of(2026,11,30),true);penalty(s,w,"PENALTY",state);
   assertThat(missionStatus(s,PeriodMission.WEEKLY_NO_PENALTY)).isEqualTo("INCOMPLETE");
  }
  for(String category:List.of("COFFEE","MEAL")) {
   var other=scope();long w=week(other,LocalDate.of(2026,11,30),true);penalty(other,w,category,"UNPAID");
   assertThat(missionStatus(other,PeriodMission.WEEKLY_NO_PENALTY)).isEqualTo("READY");
   for(var d:MissionWindow.forMission(PeriodMission.MONTHLY_NO_PENALTY,NOW).weeks())if(!d.equals(LocalDate.of(2026,11,30)))week(other,d,true);
   assertThat(missionStatus(other,PeriodMission.MONTHLY_NO_PENALTY)).isEqualTo("READY");
  }
  var corrected=scope();long c=week(corrected,LocalDate.of(2026,11,30),true);penalty(corrected,c,"PENALTY","CANCELED");
  assertThat(missionStatus(corrected,PeriodMission.WEEKLY_NO_PENALTY)).isEqualTo("READY");
  jdbc.update("UPDATE weekly_devotion_records SET submitted_at=NULL WHERE id=?",c);
  assertThat(missionStatus(corrected,PeriodMission.WEEKLY_NO_PENALTY)).isEqualTo("INCOMPLETE");
 }
 @Test void editing_old_vote_and_empty_response_do_not_create_new_participation() {
  var s=scope();vote(s,NOW.minusSeconds(8*86400),true);vote(s,NOW.minusSeconds(60),false);
  assertThat(missionStatus(s,PeriodMission.WEEKLY_VOTE)).isEqualTo("INCOMPLETE");
  vote(s,NOW.minusSeconds(60),true);
  assertThat(missionStatus(s,PeriodMission.WEEKLY_VOTE)).isEqualTo("READY");
 }
 @Test void concurrent_claims_credit_once_and_outer_failure_rolls_back()throws Exception {
  var s=scope();week(s,LocalDate.of(2026,12,7),true);
  var pool=Executors.newFixedThreadPool(2);
  try {
   var start=new CountDownLatch(1);
   Callable<Long> task=()->{start.await();return missions.claim(s.campus,s.user,PeriodMission.WEEKLY_SUBMISSION).balance();};
   var a=pool.submit(task);var b=pool.submit(task);start.countDown();
   assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(1100);assertThat(b.get(30,TimeUnit.SECONDS)).isEqualTo(1100);
  }finally{pool.shutdownNow();}
  var other=scope();week(other,LocalDate.of(2026,12,7),true);
  assertThatThrownBy(()->new TransactionTemplate(tx).executeWithoutResult(status->{missions.claim(other.campus,other.user,PeriodMission.WEEKLY_SUBMISSION);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
  assertThat(wallet.wallet(other.user).balance()).isEqualTo(1000);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM mission_claims WHERE user_id=?",Long.class,other.user)).isZero();
  assertThat(missionStatus(other,PeriodMission.WEEKLY_SUBMISSION)).isEqualTo("READY");
 }
 @Test void later_admin_correction_does_not_auto_debit_and_other_users_are_forbidden() {
  var s=scope();long w=week(s,LocalDate.of(2026,12,7),true);
  missions.claim(s.campus,s.user,PeriodMission.WEEKLY_SUBMISSION);
  jdbc.update("UPDATE weekly_devotion_records SET submitted_at=NULL WHERE id=?",w);
  missions.claim(s.campus,s.user,PeriodMission.WEEKLY_SUBMISSION);
  assertThat(wallet.wallet(s.user).balance()).isEqualTo(1100);
  var other=scope();assertThatThrownBy(()->missions.get(s.campus,other.user)).isInstanceOf(RuntimeException.class);
  jdbc.update("UPDATE campus_members SET status='INACTIVE' WHERE campus_id=? AND user_id=?",s.campus,s.user);
  assertThatThrownBy(()->missions.claim(s.campus,s.user,PeriodMission.WEEKLY_SUBMISSION)).isInstanceOf(RuntimeException.class);
 }
 @Test void http_authentication_periods_claim_replay_and_docs()throws Exception {
  var s=scope();String email=UUID.randomUUID()+"@example.test";
  jdbc.update("UPDATE users SET email=?,password_hash=? WHERE id=?",email,passwords.encode("1234"),s.user);
  week(s,LocalDate.of(2026,12,7),true);
  String login=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password","1234")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String token=json.readTree(login).path("data").path("accessToken").asText();String path="/api/v1/campuses/"+s.campus+"/missions/me";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.missions.length()").value(5)).andDo(org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document("period-missions"));
  mvc.perform(post(path+"/WEEKLY_SUBMISSION/claims").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.balance").value(1100)).andDo(org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document("period-mission-claim"));
  mvc.perform(post(path+"/WEEKLY_SUBMISSION/claims").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.balance").value(1100));
  mvc.perform(post(path+"/WEEKLY_VOTE/claims").header("Authorization","Bearer "+token)).andExpect(status().isConflict());
  mvc.perform(post(path+"/invalid/claims").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
 }
}
