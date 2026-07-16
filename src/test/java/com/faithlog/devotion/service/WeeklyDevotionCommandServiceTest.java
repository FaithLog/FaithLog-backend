package com.faithlog.devotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.devotion.domain.DevotionFineCalculator;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.PenaltyRuleRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.devotion.service.command.DevotionDailyCheckCommand;
import com.faithlog.devotion.service.command.UpdateWeeklyDevotionCommand;
import com.faithlog.devotion.service.port.DevotionPenaltyChargePort;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WeeklyDevotionCommandServiceTest {

	private static final Long CAMPUS_ID = 197L;
	private static final Long USER_ID = 1_001L;
	private static final Long WEEKLY_RECORD_ID = 1_971L;
	private static final LocalDate WEEK_START_DATE = LocalDate.of(2026, 7, 27);

	@Mock
	private WeeklyDevotionRecordRepository weeklyRecordRepository;
	@Mock
	private DevotionDailyCheckRepository dailyCheckRepository;
	@Mock
	private PenaltyRuleRepository penaltyRuleRepository;
	@Mock
	private CampusRepositoryPort campusRepository;
	@Mock
	private CampusMemberRepositoryPort campusMemberRepository;
	@Mock
	private CampusUserLookupPort userLookupPort;
	@Mock
	private DevotionFineCalculator fineCalculator;
	@Mock
	private DevotionPenaltyChargePort penaltyChargePort;

	private WeeklyDevotionCommandService service;

	@BeforeEach
	void setUp() {
		service = new WeeklyDevotionCommandService(
			weeklyRecordRepository,
			dailyCheckRepository,
			penaltyRuleRepository,
			campusRepository,
			campusMemberRepository,
			userLookupPort,
			fineCalculator,
			penaltyChargePort
		);
	}

	@Test
	void updateWeeklyCheck_bulk_loads_daily_rows_once_and_saves_missing_rows_once() {
		WeeklyDevotionRecord weeklyRecord = WeeklyDevotionRecord.create(CAMPUS_ID, USER_ID, WEEK_START_DATE);
		ReflectionTestUtils.setField(weeklyRecord, "id", WEEKLY_RECORD_ID);
		Campus campus = Campus.create("성능캠", "분당", null, "PERF197");
		ReflectionTestUtils.setField(campus, "id", CAMPUS_ID);

		when(userLookupPort.findCampusUserById(USER_ID)).thenReturn(Optional.of(
			new CampusUserLookupResult(USER_ID, "성능회원", "perf197@example.com", "USER", true)
		));
		when(campusMemberRepository.findByCampusIdAndUserId(CAMPUS_ID, USER_ID))
			.thenReturn(Optional.of(CampusMember.createMember(CAMPUS_ID, USER_ID)));
		when(weeklyRecordRepository.findByCampusIdAndUserIdAndWeekStartDateForUpdate(
			CAMPUS_ID,
			USER_ID,
			WEEK_START_DATE
		)).thenReturn(Optional.of(weeklyRecord));
		when(dailyCheckRepository.findByWeeklyRecordIdOrderByRecordDateAsc(WEEKLY_RECORD_ID))
			.thenReturn(List.of());
		when(campusRepository.findById(CAMPUS_ID)).thenReturn(Optional.of(campus));

		service.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			CAMPUS_ID,
			USER_ID,
			WEEK_START_DATE,
			List.of(
				new DevotionDailyCheckCommand(WEEK_START_DATE, true, true, true),
				new DevotionDailyCheckCommand(WEEK_START_DATE.plusDays(2), true, false, true)
			),
			5,
			false
		));

		verify(dailyCheckRepository).findByWeeklyRecordIdOrderByRecordDateAsc(WEEKLY_RECORD_ID);
		verify(dailyCheckRepository, never()).findByWeeklyRecordIdAndRecordDate(anyLong(), any(LocalDate.class));
		verify(dailyCheckRepository, never()).save(any(DevotionDailyCheck.class));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Iterable<DevotionDailyCheck>> savedChecksCaptor = ArgumentCaptor.forClass(Iterable.class);
		verify(dailyCheckRepository).saveAll(savedChecksCaptor.capture());
		List<DevotionDailyCheck> savedChecks = StreamSupport
			.stream(savedChecksCaptor.getValue().spliterator(), false)
			.toList();
		assertThat(savedChecks).hasSize(7);
		assertThat(savedChecks)
			.extracting(DevotionDailyCheck::recordDate)
			.containsExactly(
				WEEK_START_DATE,
				WEEK_START_DATE.plusDays(1),
				WEEK_START_DATE.plusDays(2),
				WEEK_START_DATE.plusDays(3),
				WEEK_START_DATE.plusDays(4),
				WEEK_START_DATE.plusDays(5),
				WEEK_START_DATE.plusDays(6)
			);
		assertThat(savedChecks.get(0).quietTimeChecked()).isTrue();
		assertThat(savedChecks.get(2).prayerChecked()).isFalse();
		assertThat(savedChecks.get(6).bibleReadingChecked()).isFalse();
	}
}
