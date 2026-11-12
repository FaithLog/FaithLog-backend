package com.faithlog.missions;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class MissionEvidenceTest {
 private final MissionWindow window=MissionWindow.forMission(PeriodMission.WEEKLY_NO_PENALTY,Instant.parse("2026-11-16T00:00:00Z"));
 private final Instant joined=Instant.parse("2026-01-01T00:00:00Z");
 @Test void missing_submission_and_positive_penalty_are_not_zero_penalty() {
  assertThat(new MissionEvidence(List.of(),List.of()).eligible(PeriodMission.WEEKLY_NO_PENALTY,window,joined)).isFalse();
  assertThat(new MissionEvidence(List.of(new MissionEvidence.Week(1L,window.start(),true)),List.of()).eligible(PeriodMission.WEEKLY_NO_PENALTY,window,joined)).isFalse();
  assertThat(new MissionEvidence(List.of(new MissionEvidence.Week(1L,window.start(),false)),List.of()).eligible(PeriodMission.WEEKLY_NO_PENALTY,window,joined)).isTrue();
 }
 @Test void joined_midweek_cannot_claim_no_penalty_but_can_submit_or_vote() {
  var midweek=MissionWindow.midnight(window.start().plusDays(1));
  var e=new MissionEvidence(List.of(new MissionEvidence.Week(1L,window.start(),false)),List.of(8L));
  assertThat(e.eligible(PeriodMission.WEEKLY_NO_PENALTY,window,midweek)).isFalse();
  assertThat(e.eligible(PeriodMission.WEEKLY_SUBMISSION,window,midweek)).isTrue();
  assertThat(e.eligible(PeriodMission.WEEKLY_VOTE,window,midweek)).isTrue();
 }
 @Test void monthly_requires_every_expected_week_even_when_rows_are_missing() {
  var w=MissionWindow.forMission(PeriodMission.MONTHLY_ALL_SUBMISSIONS,Instant.parse("2026-12-07T00:00:00Z"));
  var weeks=w.weeks().stream().map(d->new MissionEvidence.Week((long)d.getDayOfMonth(),d,false)).toList();
  assertThat(new MissionEvidence(weeks,List.of()).eligible(PeriodMission.MONTHLY_ALL_SUBMISSIONS,w,joined)).isTrue();
  assertThat(new MissionEvidence(weeks.subList(1,weeks.size()),List.of()).eligible(PeriodMission.MONTHLY_ALL_SUBMISSIONS,w,joined)).isFalse();
  assertThat(new MissionEvidence(weeks,List.of()).eligible(PeriodMission.MONTHLY_ALL_SUBMISSIONS,w,MissionWindow.midnight(w.start().plusDays(1)))).isFalse();
 }
}
