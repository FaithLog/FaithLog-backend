package com.faithlog.missions;

import com.faithlog.points.PointWalletService;
import com.faithlog.global.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeriodMissionService {
 private final PointWalletService wallet;
 private final NamedParameterJdbcTemplate jdbc;
 private final ObjectMapper json;
 private final Clock clock;
 public PeriodMissionService(PointWalletService wallet,NamedParameterJdbcTemplate jdbc,ObjectMapper json,Clock clock) {
  this.wallet=wallet;this.jdbc=jdbc;this.json=json;this.clock=clock;
 }
 @Transactional public Snapshot get(Long campusId,Long userId) {
  Instant joined=lockMember(campusId,userId);
  return snapshot(campusId,userId,joined,clock.instant());
 }
 @Transactional public Snapshot claim(Long campusId,Long userId,PeriodMission mission) {
  if(mission==null)throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
  Instant joined=lockMember(campusId,userId),now=clock.instant();
  var window=MissionWindow.forMission(mission,now);
  if(!claimed(userId,mission,window)) {
   var evidence=evidence(campusId,userId,joined,now,mission,window);
   if(!evidence.eligible(mission,window,joined))throw new BusinessException(ErrorCode.PERIOD_MISSION_NOT_READY);
   String business="mission:"+mission.name()+":"+window.start();
   wallet.settle(userId,mission.points(),business);
   String source;
   try {source=json.writeValueAsString(new EvidenceSnapshot("period-v1",window,joined,evidence));}
   catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException("Mission evidence serialization failed",e);}
   jdbc.update("INSERT INTO mission_claims(user_id,mission_code,period_start,source_campus_id,points,business_key,evidence_json,claimed_at) VALUES(:user,:mission,:start,:campus,:points,:business,CAST(:source AS jsonb),:now)",params(campusId,userId).addValue("mission",mission.name()).addValue("start",window.start()).addValue("points",mission.points()).addValue("business",business).addValue("source",source).addValue("now",Timestamp.from(now)));
  }
  return snapshot(campusId,userId,joined,now);
 }
 private Instant lockMember(Long campusId,Long userId) {
  wallet.lockAccount(userId);
  var joined=jdbc.query("SELECT joined_at FROM campus_members WHERE campus_id=:campus AND user_id=:user AND status='ACTIVE' FOR UPDATE",params(campusId,userId),(rs,n)->rs.getTimestamp("joined_at").toInstant());
  if(joined.isEmpty())throw new BusinessException(ErrorCode.DEVOTION_ACCESS_FORBIDDEN);
  return joined.getFirst();
 }
 private boolean claimed(Long user,PeriodMission mission,MissionWindow window) {
  return jdbc.queryForObject("SELECT count(*) FROM mission_claims WHERE user_id=:user AND mission_code=:mission AND period_start=:start",new MapSqlParameterSource("user",user).addValue("mission",mission.name()).addValue("start",window.start()),Long.class)>0;
 }
 private Snapshot snapshot(Long campus,Long user,Instant joined,Instant now) {
  var windows=new EnumMap<PeriodMission,MissionWindow>(PeriodMission.class);
  for(var mission:PeriodMission.values())windows.put(mission,MissionWindow.forMission(mission,now));
  var starts=windows.values().stream().map(MissionWindow::start).distinct().toList();
  var claimedKeys=new HashSet<>(jdbc.query("SELECT mission_code,period_start FROM mission_claims WHERE user_id=:user AND period_start IN (:starts)",new MapSqlParameterSource("user",user).addValue("starts",starts),(rs,n)->rs.getString("mission_code")+":"+rs.getDate("period_start").toLocalDate()));
  var pending=windows.entrySet().stream().filter(e->!claimedKeys.contains(e.getKey().name()+":"+e.getValue().start())).toList();
  var dates=pending.stream().filter(e->e.getKey()!=PeriodMission.WEEKLY_VOTE).flatMap(e->e.getValue().weeks().stream()).distinct().sorted().toList();
  var allWeeks=dates.isEmpty()?List.<MissionEvidence.Week>of():submittedWeeks(campus,user,joined,now,dates);
  var vote=pending.stream().anyMatch(e->e.getKey()==PeriodMission.WEEKLY_VOTE)?evidence(campus,user,joined,now,PeriodMission.WEEKLY_VOTE,windows.get(PeriodMission.WEEKLY_VOTE)):new MissionEvidence(List.of(),List.of());
  var items=new ArrayList<Item>();
  for(var mission:PeriodMission.values()) {
   var window=windows.get(mission);
   var source=mission==PeriodMission.WEEKLY_VOTE?vote:new MissionEvidence(allWeeks.stream().filter(w->window.weeks().contains(w.start())).toList(),List.of());
   String status=claimedKeys.contains(mission.name()+":"+window.start())?"CLAIMED":source.eligible(mission,window,joined)?"READY":"INCOMPLETE";
   items.add(new Item(mission,mission.points(),status,window.start(),window.end(),window.availableAt(),window.expiresAt()));
  }
  return new Snapshot(now,wallet.wallet(user).balance(),List.copyOf(items));
 }
 private MissionEvidence evidence(Long campus,Long user,Instant joined,Instant now,PeriodMission mission,MissionWindow window) {
  var p=params(campus,user).addValue("joined",Timestamp.from(joined)).addValue("now",Timestamp.from(now));
  if(mission==PeriodMission.WEEKLY_VOTE) {
   p.addValue("from",Timestamp.from(MissionWindow.midnight(window.start()))).addValue("until",Timestamp.from(window.expiresAt()));
   var responses=jdbc.queryForList("SELECT r.id FROM poll_responses r JOIN polls p ON p.id=r.poll_id WHERE r.user_id=:user AND p.campus_id=:campus AND p.status IN ('OPEN','CLOSED') AND r.created_at>=:from AND r.created_at<:until AND r.created_at>=:joined AND r.created_at<=:now AND EXISTS(SELECT 1 FROM poll_response_options ro JOIN poll_options o ON o.id=ro.option_id AND o.poll_id=p.id WHERE ro.response_id=r.id) ORDER BY r.created_at,r.id LIMIT 1",p,Long.class);
   return new MissionEvidence(List.of(),responses);
  }
  return new MissionEvidence(submittedWeeks(campus,user,joined,now,window.weeks()),List.of());
 }
 private List<MissionEvidence.Week> submittedWeeks(Long campus,Long user,Instant joined,Instant now,List<LocalDate> dates) {
  var p=params(campus,user).addValue("joined",Timestamp.from(joined)).addValue("now",Timestamp.from(now)).addValue("weeks",dates);
  // One statement reads submission and penalty state from the same MVCC snapshot.
  // A CANCELED penalty reopens submission; a zero-penalty resubmission keeps that row canceled.
  var weeks=jdbc.query("SELECT w.id,w.week_start_date,EXISTS(SELECT 1 FROM charge_items c WHERE c.campus_id=w.campus_id AND c.user_id=w.user_id AND c.source_id=w.id AND c.source_type='DEVOTION_RECORD' AND c.payment_category='PENALTY' AND c.amount>0 AND c.status<>'CANCELED') AS penalty FROM weekly_devotion_records w WHERE w.campus_id=:campus AND w.user_id=:user AND w.week_start_date IN (:weeks) AND w.submitted_at IS NOT NULL AND w.submitted_at>=:joined AND w.submitted_at<=:now ORDER BY w.week_start_date",p,(rs,n)->new MissionEvidence.Week(rs.getLong("id"),rs.getDate("week_start_date").toLocalDate(),rs.getBoolean("penalty")));
  return weeks;
 }
 private static MapSqlParameterSource params(Long campus,Long user) {return new MapSqlParameterSource("campus",campus).addValue("user",user);}
 private record EvidenceSnapshot(String policyVersion,MissionWindow window,Instant joinedAt,MissionEvidence evidence) {}
 public record Item(PeriodMission mission,long points,String status,LocalDate periodStart,LocalDate periodEnd,Instant availableAt,Instant expiresAt) {}
 public record Snapshot(Instant serverNow,long balance,List<Item> missions) {}
}
