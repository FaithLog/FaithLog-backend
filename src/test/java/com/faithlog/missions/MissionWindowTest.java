package com.faithlog.missions;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class MissionWindowTest {
 @Test void week_resets_at_seoul_monday_midnight() {
  var before=MissionWindow.forMission(PeriodMission.WEEKLY_SUBMISSION,Instant.parse("2026-11-15T14:59:59Z"));
  var after=MissionWindow.forMission(PeriodMission.WEEKLY_SUBMISSION,Instant.parse("2026-11-15T15:00:00Z"));
  assertThat(before.start()).isEqualTo(LocalDate.of(2026,11,9));
  assertThat(after.start()).isEqualTo(LocalDate.of(2026,11,16));
  assertThat(MissionWindow.forMission(PeriodMission.WEEKLY_NO_PENALTY,Instant.parse("2026-11-15T15:00:00Z")).start()).isEqualTo(before.start());
 }
 @Test void monthly_waits_until_last_monday_week_is_finished() {
  var waiting=MissionWindow.forMission(PeriodMission.MONTHLY_ALL_SUBMISSIONS,Instant.parse("2026-12-01T00:00:00Z"));
  var opened=MissionWindow.forMission(PeriodMission.MONTHLY_ALL_SUBMISSIONS,Instant.parse("2026-12-06T15:00:00Z"));
  assertThat(waiting.start()).isEqualTo(LocalDate.of(2026,10,1));
  assertThat(opened.start()).isEqualTo(LocalDate.of(2026,11,1));
  assertThat(opened.availableAt()).isEqualTo(Instant.parse("2026-12-06T15:00:00Z"));
  assertThat(opened.weeks()).containsExactly(LocalDate.of(2026,11,2),LocalDate.of(2026,11,9),LocalDate.of(2026,11,16),LocalDate.of(2026,11,23),LocalDate.of(2026,11,30));
 }
 @Test void sunday_month_end_and_leap_year_use_calendar_boundaries() {
  var feb=MissionWindow.forMission(PeriodMission.MONTHLY_NO_PENALTY,Instant.parse("2028-03-05T15:00:00Z"));
  assertThat(feb.start()).isEqualTo(LocalDate.of(2028,2,1));
  assertThat(feb.end()).isEqualTo(LocalDate.of(2028,2,29));
  assertThat(feb.weeks()).hasSize(4);
 }
}
