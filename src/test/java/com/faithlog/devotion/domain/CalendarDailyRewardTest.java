package com.faithlog.devotion.domain;

import static org.assertj.core.api.Assertions.*;
import com.faithlog.devotion.domain.entity.CalendarDailyReward;
import com.faithlog.devotion.domain.type.DailyMission;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CalendarDailyRewardTest {
 @Test void requires_calendar_save_and_claims_only_once() {
  var reward = new CalendarDailyReward(1L, 2L, LocalDate.of(2026,11,11), DailyMission.QUIET_TIME);
  assertThat(reward.ready(true)).isFalse();
  reward.recordSave(true);
  assertThat(reward.ready(false)).isFalse();
  assertThat(reward.ready(true)).isTrue();
  reward.claim(Instant.parse("2026-11-10T22:00:00Z"), true);
  assertThat(reward.points()).isEqualTo(30);
  reward.recordSave(false);
  reward.recordSave(true);
  reward.claim(Instant.parse("2026-11-10T23:00:00Z"), true);
  assertThat(reward.claimedAt()).isEqualTo(Instant.parse("2026-11-10T22:00:00Z"));
  assertThat(reward.ready(true)).isFalse();
 }
 @Test void refuses_unsaved_or_now_unchecked_activity() {
  var reward = new CalendarDailyReward(1L, 2L, LocalDate.of(2026,11,11), DailyMission.PRAYER);
  assertThatThrownBy(() -> reward.claim(Instant.now(), true)).isInstanceOf(RuntimeException.class);
  reward.recordSave(true);
  assertThatThrownBy(() -> reward.claim(Instant.now(), false)).isInstanceOf(RuntimeException.class);
 }
}
