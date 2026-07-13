package com.faithlog.devotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.devotion.service.query.GetAdminWeeklyDevotionMembersQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminWeeklyDevotionQueryServiceTest {

	private static final Long CAMPUS_ID = 1L;
	private static final LocalDate WEEK_START_DATE = LocalDate.of(2026, 7, 13);

	@Mock
	private CampusRepositoryPort campusRepository;
	@Mock
	private CampusMemberRepositoryPort campusMemberRepository;
	@Mock
	private CampusUserLookupPort userLookupPort;
	@Mock
	private WeeklyDevotionRecordRepository weeklyRecordRepository;
	@Mock
	private DevotionDailyCheckRepository dailyCheckRepository;
	@Mock
	private ChargeItemRepository chargeItemRepository;

	private AdminWeeklyDevotionQueryService service;

	@BeforeEach
	void setUp() {
		service = new AdminWeeklyDevotionQueryService(
			campusRepository,
			campusMemberRepository,
			userLookupPort,
			weeklyRecordRepository,
			dailyCheckRepository,
			chargeItemRepository
		);
	}

	@Test
	void loads_members_users_weekly_records_daily_checks_and_charges_with_one_bulk_call_each() {
		Campus campus = Campus.create("벌크캠", "분당", null, "BULK1234");
		ReflectionTestUtils.setField(campus, "id", CAMPUS_ID);
		CampusMember firstMember = CampusMember.createMember(CAMPUS_ID, 11L);
		CampusMember secondMember = CampusMember.createMember(CAMPUS_ID, 12L);
		WeeklyDevotionRecord firstRecord = submittedRecord(101L, 11L);
		WeeklyDevotionRecord secondRecord = submittedRecord(102L, 12L);
		DevotionDailyCheck firstCheck = DevotionDailyCheck.create(101L, WEEK_START_DATE, true, true, true);
		ChargeItem firstCharge = charge(201L, 11L, 101L, 2500, ChargeStatus.UNPAID);
		ChargeItem secondCharge = charge(202L, 12L, 102L, 1200, ChargeStatus.PAID);

		when(userLookupPort.findCampusUserById(99L)).thenReturn(Optional.of(
			new CampusUserLookupResult(99L, "서비스관리자", "admin@example.com", "ADMIN", true)
		));
		when(campusRepository.findById(CAMPUS_ID)).thenReturn(Optional.of(campus));
		when(campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(CAMPUS_ID, CampusMemberStatus.ACTIVE))
			.thenReturn(List.of(firstMember, secondMember));
		when(userLookupPort.findCampusUsersByIds(anyCollection())).thenReturn(List.of(
			new CampusUserLookupResult(11L, "첫째", "first@example.com", "USER", true),
			new CampusUserLookupResult(12L, "둘째", "second@example.com", "USER", true)
		));
		when(weeklyRecordRepository.findByCampusIdAndWeekStartDate(CAMPUS_ID, WEEK_START_DATE))
			.thenReturn(List.of(firstRecord, secondRecord));
		when(dailyCheckRepository.findByWeeklyRecordIdInAndRecordDateBetweenOrderByRecordDateAsc(
			List.of(101L, 102L),
			WEEK_START_DATE,
			WEEK_START_DATE.plusDays(6)
		)).thenReturn(List.of(firstCheck));
		when(chargeItemRepository.findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInOrderByIdAsc(
			CAMPUS_ID,
			PaymentCategory.PENALTY,
			ChargeSourceType.DEVOTION_RECORD,
			List.of(101L, 102L)
		)).thenReturn(List.of(firstCharge, secondCharge));

		var result = service.getWeeklyMembers(new GetAdminWeeklyDevotionMembersQuery(
			CAMPUS_ID,
			99L,
			WEEK_START_DATE
		));

		assertThat(result.submittedMembers()).hasSize(2);
		assertThat(result.totalPenaltyAmount()).isEqualTo(3700);
		verify(campusMemberRepository).findByCampusIdAndStatusOrderByIdAsc(CAMPUS_ID, CampusMemberStatus.ACTIVE);
		verify(userLookupPort).findCampusUsersByIds(List.of(11L, 12L));
		verify(weeklyRecordRepository).findByCampusIdAndWeekStartDate(CAMPUS_ID, WEEK_START_DATE);
		verify(dailyCheckRepository).findByWeeklyRecordIdInAndRecordDateBetweenOrderByRecordDateAsc(
			List.of(101L, 102L),
			WEEK_START_DATE,
			WEEK_START_DATE.plusDays(6)
		);
		verify(chargeItemRepository).findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInOrderByIdAsc(
			CAMPUS_ID,
			PaymentCategory.PENALTY,
			ChargeSourceType.DEVOTION_RECORD,
			List.of(101L, 102L)
		);
		verify(campusMemberRepository, never()).findByCampusIdAndUserId(CAMPUS_ID, 99L);
	}

	private WeeklyDevotionRecord submittedRecord(Long id, Long userId) {
		WeeklyDevotionRecord record = WeeklyDevotionRecord.create(CAMPUS_ID, userId, WEEK_START_DATE);
		ReflectionTestUtils.setField(record, "id", id);
		record.submit(Instant.parse("2026-07-19T01:00:00Z"));
		return record;
	}

	private ChargeItem charge(Long id, Long userId, Long sourceId, int amount, ChargeStatus status) {
		ChargeItem charge = ChargeItem.create(
			CAMPUS_ID,
			userId,
			PaymentCategory.PENALTY,
			1L,
			"테스트은행",
			"000-0000",
			"테스트",
			ChargeSourceType.DEVOTION_RECORD,
			sourceId,
			"경건생활 벌금",
			null,
			amount,
			null
		);
		ReflectionTestUtils.setField(charge, "id", id);
		if (status == ChargeStatus.PAID) {
			charge.markPaid(Instant.parse("2026-07-20T01:00:00Z"));
		}
		return charge;
	}
}
