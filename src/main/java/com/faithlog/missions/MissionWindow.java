package com.faithlog.missions;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Server-selected period; callers cannot claim arbitrary historical dates. */
public record MissionWindow(LocalDate start,LocalDate end,Instant availableAt,Instant expiresAt,List<LocalDate> weeks) {
 public static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
 public MissionWindow {weeks=List.copyOf(weeks);}
 public static MissionWindow forMission(PeriodMission mission,Instant now) {
  LocalDate today=now.atZone(SEOUL).toLocalDate();
  if(mission.monthly()) {
   YearMonth month=YearMonth.from(today).minusMonths(1);
   if(today.isBefore(monthClose(month)))month=month.minusMonths(1);
   LocalDate firstMonday=month.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
   var weeks=firstMonday.datesUntil(month.atEndOfMonth().plusDays(1),Period.ofWeeks(1)).toList();
   return new MissionWindow(month.atDay(1),month.atEndOfMonth(),midnight(monthClose(month)),midnight(monthClose(month.plusMonths(1))),weeks);
  }
  LocalDate monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  if(mission==PeriodMission.WEEKLY_NO_PENALTY) {
   LocalDate previous=monday.minusWeeks(1);
   return new MissionWindow(previous,previous.plusDays(6),midnight(monday),midnight(monday.plusWeeks(1)),List.of(previous));
  }
  return new MissionWindow(monday,monday.plusDays(6),midnight(monday),midnight(monday.plusWeeks(1)),List.of(monday));
 }
 private static LocalDate monthClose(YearMonth month) {
  return month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
 }
 public static Instant midnight(LocalDate date) {return date.atStartOfDay(SEOUL).toInstant();}
}
