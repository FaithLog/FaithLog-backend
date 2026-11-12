package com.faithlog.devotion.domain.entity;

import com.faithlog.devotion.domain.type.DailyMission;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name="calendar_daily_rewards", uniqueConstraints=@UniqueConstraint(name="uk_calendar_reward_scope", columnNames={"campus_id","user_id","record_date","mission"}))
public class CalendarDailyReward {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="campus_id",nullable=false) private Long campusId;
 @Column(name="user_id",nullable=false) private Long userId;
 @Column(name="record_date",nullable=false) private LocalDate recordDate;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private DailyMission mission;
 @Column(name="calendar_checked",nullable=false) private boolean calendarChecked;
 @Column(name="claimed_at") private Instant claimedAt;
 @Column(nullable=false) private int points;
 protected CalendarDailyReward() {}
 public CalendarDailyReward(Long campusId, Long userId, LocalDate date, DailyMission mission) {
  this.campusId=campusId; this.userId=userId; this.recordDate=date; this.mission=mission;
 }
 public void recordSave(boolean checked) {calendarChecked=checked;}
 public boolean ready(boolean currentlyChecked) {return calendarChecked && currentlyChecked && claimedAt==null;}
 public void claim(Instant now, boolean currentlyChecked) {
  if (claimedAt!=null) return;
  if (!ready(currentlyChecked)) throw new BusinessException(ErrorCode.DEVOTION_MISSION_NOT_READY);
  claimedAt=now; points=30;
 }
 public DailyMission mission() {return mission;}
 public Instant claimedAt() {return claimedAt;}
 public int points() {return points;}
}
