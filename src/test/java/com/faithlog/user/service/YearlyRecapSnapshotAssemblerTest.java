package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.user.service.port.CampusRecapActivity;
import com.faithlog.user.service.port.CommentActivityRecapAggregate;
import com.faithlog.user.service.port.DevotionDailyActivity;
import com.faithlog.user.service.port.DevotionRecapSource;
import com.faithlog.user.service.port.PenaltySummaryRecapAggregate;
import com.faithlog.user.service.port.PrayerRecapAggregate;
import com.faithlog.user.service.result.YearlyRecapSnapshotData;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class YearlyRecapSnapshotAssemblerTest {

	private final YearlyRecapSnapshotAssembler assembler = new YearlyRecapSnapshotAssembler();

	@Test
	void assembles_counts_streak_and_the_earliest_month_when_activity_is_tied() {
		DevotionRecapSource devotion = new DevotionRecapSource(List.of(
			new DevotionDailyActivity(LocalDate.of(2026, 1, 1), true, true, true),
			new DevotionDailyActivity(LocalDate.of(2026, 1, 2), true, true, true),
			new DevotionDailyActivity(LocalDate.of(2026, 2, 1), true, true, true),
			new DevotionDailyActivity(LocalDate.of(2026, 2, 2), true, true, true)
		), 40);
		YearlyRecapSnapshotData result = assembler.assemble(
			2026,
			List.of(new CampusRecapActivity(10L, "서울 캠퍼스", LocalDate.of(2026, 3, 10))),
			devotion,
			new PrayerRecapAggregate(22, 2),
			new CommentActivityRecapAggregate(6),
			new PenaltySummaryRecapAggregate(4, 12_000, 3, 9_000)
		);

		assertThat(result.hasRecapData()).isTrue();
		assertThat(result.campuses()).singleElement().satisfies(campus -> {
			assertThat(campus.campusId()).isEqualTo(10L);
			assertThat(campus.joinedDuringRecapYear()).isTrue();
		});
		assertThat(result.devotion().quietTimeCount()).isEqualTo(4);
		assertThat(result.devotion().bibleReadingCount()).isEqualTo(4);
		assertThat(result.devotion().prayerCount()).isEqualTo(4);
		assertThat(result.devotion().allCompletedDayCount()).isEqualTo(4);
		assertThat(result.devotion().submittedWeekCount()).isEqualTo(40);
		assertThat(result.devotion().longestStreakDays()).isEqualTo(2);
		assertThat(result.devotion().mostActiveMonth()).isEqualTo(1);
		assertThat(result.prayerActivity().submittedWeekCount()).isEqualTo(22);
		assertThat(result.prayerActivity().participatedSeasonCount()).isEqualTo(2);
		assertThat(result.commentActivity().writtenCount()).isEqualTo(6);
		assertThat(result.penaltySummary().totalCount()).isEqualTo(7);
		assertThat(result.penaltySummary().totalAmount()).isEqualTo(21_000);
		assertThat(result.penaltySummary().paidCount()).isEqualTo(4);
		assertThat(result.penaltySummary().unpaidCount()).isEqualTo(3);
	}

	@Test
	void returns_an_exact_zero_payload_when_no_recap_data_exists() {
		YearlyRecapSnapshotData result = assembler.assemble(
			2026,
			List.of(),
			new DevotionRecapSource(List.of(), 0),
			new PrayerRecapAggregate(0, 0),
			new CommentActivityRecapAggregate(0),
			new PenaltySummaryRecapAggregate(0, 0, 0, 0)
		);

		assertThat(result.hasRecapData()).isFalse();
		assertThat(result.campuses()).isEmpty();
		assertThat(result.devotion().quietTimeCount()).isZero();
		assertThat(result.devotion().bibleReadingCount()).isZero();
		assertThat(result.devotion().prayerCount()).isZero();
		assertThat(result.devotion().allCompletedDayCount()).isZero();
		assertThat(result.devotion().submittedWeekCount()).isZero();
		assertThat(result.devotion().longestStreakDays()).isZero();
		assertThat(result.devotion().mostActiveMonth()).isNull();
		assertThat(result.prayerActivity().submittedWeekCount()).isZero();
		assertThat(result.prayerActivity().participatedSeasonCount()).isZero();
		assertThat(result.commentActivity().writtenCount()).isZero();
		assertThat(result.penaltySummary().totalCount()).isZero();
		assertThat(result.penaltySummary().totalAmount()).isZero();
	}

	@Test
	void an_active_campus_journey_is_recap_data_even_without_activity_rows() {
		YearlyRecapSnapshotData result = assembler.assemble(
			2026,
			List.of(new CampusRecapActivity(10L, "서울 캠퍼스", LocalDate.of(2025, 5, 1))),
			new DevotionRecapSource(List.of(), 0),
			new PrayerRecapAggregate(0, 0),
			new CommentActivityRecapAggregate(0),
			new PenaltySummaryRecapAggregate(0, 0, 0, 0)
		);

		assertThat(result.hasRecapData()).isTrue();
		assertThat(result.campuses()).singleElement()
			.extracting(campus -> campus.joinedDuringRecapYear())
			.isEqualTo(false);
		assertThat(result.devotion().mostActiveMonth()).isNull();
	}

	@Test
	void counts_a_calendar_date_once_when_multiple_campuses_have_daily_rows() {
		LocalDate sameDate = LocalDate.of(2026, 6, 1);
		YearlyRecapSnapshotData result = assembler.assemble(
			2026,
			List.of(),
			new DevotionRecapSource(List.of(
				new DevotionDailyActivity(sameDate, true, false, true),
				new DevotionDailyActivity(sameDate, false, true, false)
			), 0),
			new PrayerRecapAggregate(0, 0),
			new CommentActivityRecapAggregate(0),
			new PenaltySummaryRecapAggregate(0, 0, 0, 0)
		);

		assertThat(result.devotion().quietTimeCount()).isEqualTo(1);
		assertThat(result.devotion().bibleReadingCount()).isEqualTo(1);
		assertThat(result.devotion().prayerCount()).isEqualTo(1);
		assertThat(result.devotion().allCompletedDayCount()).isEqualTo(1);
		assertThat(result.devotion().longestStreakDays()).isEqualTo(1);
		assertThat(result.devotion().mostActiveMonth()).isEqualTo(6);
	}
}
