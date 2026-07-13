package com.faithlog.devotion.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminWeeklyDevotionControllerTest {

	private static final LocalDate WEEK_START_DATE = LocalDate.of(2026, 7, 13);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private WeeklyDevotionRecordRepository weeklyRecordRepository;

	@Autowired
	private DevotionDailyCheckRepository dailyCheckRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Test
	void weekly_members_allows_service_admin_and_active_campus_admin_roles_only() throws Exception {
		String serviceAdminToken = signupAndLogin("weekly-admin-service@example.com", UserRole.ADMIN, "서비스관리자");
		String creatorToken = signupAndLogin("weekly-admin-creator@example.com", UserRole.MANAGER, "담임");
		String ministerToken = signupAndLogin("weekly-admin-minister@example.com", UserRole.USER, "교역자");
		String elderToken = signupAndLogin("weekly-admin-elder@example.com", UserRole.USER, "장로");
		String leaderToken = signupAndLogin("weekly-admin-leader@example.com", UserRole.USER, "캠퍼스리더");
		String memberToken = signupAndLogin("weekly-admin-member@example.com", UserRole.USER, "멤버");
		String otherCampusAdminToken = signupAndLogin("weekly-admin-other@example.com", UserRole.MANAGER, "다른캠관리자");
		JsonNode campus = createCampus(creatorToken, "주간권한캠");
		long campusId = campus.path("campusId").asLong();
		String inviteCode = campus.path("inviteCode").asText();
		updateCampusRole(joinCampus(ministerToken, inviteCode), CampusRole.MINISTER);
		updateCampusRole(joinCampus(elderToken, inviteCode), CampusRole.ELDER);
		updateCampusRole(joinCampus(leaderToken, inviteCode), CampusRole.CAMPUS_LEADER);
		joinCampus(memberToken, inviteCode);
		createCampus(otherCampusAdminToken, "다른주간권한캠");

		for (String token : List.of(serviceAdminToken, creatorToken, ministerToken, elderToken, leaderToken)) {
			mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/devotions/weeks/{weekStartDate}/members",
					campusId,
					WEEK_START_DATE)
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		}

		for (String token : List.of(memberToken, otherCampusAdminToken)) {
			mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/devotions/weeks/{weekStartDate}/members",
					campusId,
					WEEK_START_DATE)
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("DEVOTION_ADMIN_FORBIDDEN"));
		}
	}

	@Test
	void weekly_members_accepts_monday_and_rejects_non_monday() throws Exception {
		String managerToken = signupAndLogin("weekly-date-manager@example.com", UserRole.MANAGER, "날짜관리자");
		long campusId = createCampus(managerToken, "주간날짜캠").path("campusId").asLong();

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/devotions/weeks/{weekStartDate}/members",
				campusId,
				WEEK_START_DATE)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/devotions/weeks/{weekStartDate}/members",
				campusId,
				WEEK_START_DATE.plusDays(1))
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DEVOTION_INVALID_WEEK_START_DATE"));
	}

	@Test
	void weekly_members_returns_active_members_actual_penalties_daily_defaults_and_cross_campus_isolation() throws Exception {
		String managerToken = signupAndLogin("weekly-data-manager@example.com", UserRole.MANAGER, "관리자");
		String paidToken = signupAndLogin("weekly-data-paid@example.com", UserRole.USER, "납부자");
		String waivedToken = signupAndLogin("weekly-data-waived@example.com", UserRole.USER, "면제자");
		String missingToken = signupAndLogin("weekly-data-missing@example.com", UserRole.USER, "미제출자");
		String draftToken = signupAndLogin("weekly-data-draft@example.com", UserRole.USER, "임시저장자");
		String inactiveToken = signupAndLogin("weekly-data-inactive@example.com", UserRole.USER, "비활성자");
		String otherManagerToken = signupAndLogin("weekly-data-other@example.com", UserRole.MANAGER, "다른캠제출자");
		JsonNode campus = createCampus(managerToken, "주간데이터캠");
		long campusId = campus.path("campusId").asLong();
		String inviteCode = campus.path("inviteCode").asText();
		long paidMembershipId = joinCampus(paidToken, inviteCode);
		long waivedMembershipId = joinCampus(waivedToken, inviteCode);
		long missingMembershipId = joinCampus(missingToken, inviteCode);
		long draftMembershipId = joinCampus(draftToken, inviteCode);
		long inactiveMembershipId = joinCampus(inactiveToken, inviteCode);
		CampusMember inactiveMember = campusMemberRepository.findById(inactiveMembershipId).orElseThrow();
		inactiveMember.deactivate();
		campusMemberRepository.saveAndFlush(inactiveMember);

		User manager = userRepository.findByEmail("weekly-data-manager@example.com").orElseThrow();
		User paid = userRepository.findByEmail("weekly-data-paid@example.com").orElseThrow();
		User waived = userRepository.findByEmail("weekly-data-waived@example.com").orElseThrow();
		User draft = userRepository.findByEmail("weekly-data-draft@example.com").orElseThrow();
		User inactive = userRepository.findByEmail("weekly-data-inactive@example.com").orElseThrow();
		WeeklyDevotionRecord managerRecord = saveWeeklyRecord(
			campusId,
			manager.id(),
			true,
			List.of(
				new DailyValue(0, true, true, false),
				new DailyValue(1, true, false, true),
				new DailyValue(5, true, true, true)
			),
			8,
			Instant.parse("2026-07-19T01:00:00Z")
		);
		WeeklyDevotionRecord paidRecord = saveWeeklyRecord(
			campusId,
			paid.id(),
			true,
			List.of(new DailyValue(2, true, true, true)),
			0,
			Instant.parse("2026-07-19T02:00:00Z")
		);
		WeeklyDevotionRecord waivedRecord = saveWeeklyRecord(
			campusId,
			waived.id(),
			true,
			List.of(),
			0,
			Instant.parse("2026-07-19T03:00:00Z")
		);
		saveWeeklyRecord(campusId, draft.id(), false, List.of(new DailyValue(0, true, true, true)), 0, null);
		WeeklyDevotionRecord inactiveRecord = saveWeeklyRecord(
			campusId,
			inactive.id(),
			true,
			List.of(new DailyValue(0, true, true, true)),
			0,
			Instant.parse("2026-07-19T04:00:00Z")
		);
		createCharge(campusId, manager.id(), managerRecord.id(), 2500, ChargeStatus.UNPAID);
		createCharge(campusId, paid.id(), paidRecord.id(), 1200, ChargeStatus.PAID);
		createCharge(campusId, waived.id(), waivedRecord.id(), 800, ChargeStatus.WAIVED);
		createCharge(campusId, inactive.id(), inactiveRecord.id(), 9999, ChargeStatus.UNPAID);

		JsonNode otherCampus = createCampus(otherManagerToken, "격리대상캠");
		long otherCampusId = otherCampus.path("campusId").asLong();
		User otherManager = userRepository.findByEmail("weekly-data-other@example.com").orElseThrow();
		WeeklyDevotionRecord otherRecord = saveWeeklyRecord(
			otherCampusId,
			otherManager.id(),
			true,
			List.of(new DailyValue(0, true, true, true)),
			0,
			Instant.parse("2026-07-19T05:00:00Z")
		);
		createCharge(otherCampusId, otherManager.id(), otherRecord.id(), 7777, ChargeStatus.UNPAID);

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/devotions/weeks/{weekStartDate}/members",
				campusId,
				WEEK_START_DATE)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.data.weekStartDate").value("2026-07-13"))
			.andExpect(jsonPath("$.data.weekEndDate").value("2026-07-19"))
			.andExpect(jsonPath("$.data.activeMemberCount").value(5))
			.andExpect(jsonPath("$.data.submittedCount").value(3))
			.andExpect(jsonPath("$.data.missingCount").value(2))
			.andExpect(jsonPath("$.data.totalPenaltyAmount").value(3700))
			.andExpect(jsonPath("$.data.submittedMembers.length()").value(3))
			.andExpect(jsonPath("$.data.submittedMembers[0].userId").value(manager.id()))
			.andExpect(jsonPath("$.data.submittedMembers[0].quietTimeCount").value(3))
			.andExpect(jsonPath("$.data.submittedMembers[0].bibleReadingCount").value(2))
			.andExpect(jsonPath("$.data.submittedMembers[0].prayerCount").value(2))
			.andExpect(jsonPath("$.data.submittedMembers[0].saturdayLateMinutes").value(8))
			.andExpect(jsonPath("$.data.submittedMembers[0].submittedAt").value("2026-07-19T01:00:00Z"))
			.andExpect(jsonPath("$.data.submittedMembers[0].penalty.chargeItemId").isNumber())
			.andExpect(jsonPath("$.data.submittedMembers[0].penalty.amount").value(2500))
			.andExpect(jsonPath("$.data.submittedMembers[0].penalty.status").value("UNPAID"))
			.andExpect(jsonPath("$.data.submittedMembers[0].dailyChecks.length()").value(7))
			.andExpect(jsonPath("$.data.submittedMembers[0].dailyChecks[0].recordDate").value("2026-07-13"))
			.andExpect(jsonPath("$.data.submittedMembers[0].dailyChecks[0].quietTimeChecked").value(true))
			.andExpect(jsonPath("$.data.submittedMembers[1].userId").value(paid.id()))
			.andExpect(jsonPath("$.data.submittedMembers[1].penalty.amount").value(1200))
			.andExpect(jsonPath("$.data.submittedMembers[1].penalty.status").value("PAID"))
			.andExpect(jsonPath("$.data.submittedMembers[2].userId").value(waived.id()))
			.andExpect(jsonPath("$.data.submittedMembers[2].quietTimeCount").value(0))
			.andExpect(jsonPath("$.data.submittedMembers[2].dailyChecks.length()").value(7))
			.andExpect(jsonPath("$.data.submittedMembers[2].dailyChecks[0].quietTimeChecked").value(false))
			.andExpect(jsonPath("$.data.submittedMembers[2].penalty.amount").value(800))
			.andExpect(jsonPath("$.data.submittedMembers[2].penalty.status").value("WAIVED"))
			.andExpect(jsonPath("$.data.missingMembers.length()").value(2))
			.andExpect(jsonPath("$.data.missingMembers[0].userId").value(
				userRepository.findByEmail("weekly-data-missing@example.com").orElseThrow().id()))
			.andExpect(jsonPath("$.data.missingMembers[1].userId").value(draft.id()));

		org.assertj.core.api.Assertions.assertThat(paidMembershipId).isPositive();
		org.assertj.core.api.Assertions.assertThat(waivedMembershipId).isPositive();
		org.assertj.core.api.Assertions.assertThat(missingMembershipId).isPositive();
		org.assertj.core.api.Assertions.assertThat(draftMembershipId).isPositive();
	}

	private WeeklyDevotionRecord saveWeeklyRecord(
		Long campusId,
		Long userId,
		boolean submitted,
		List<DailyValue> values,
		int saturdayLateMinutes,
		Instant submittedAt
	) {
		WeeklyDevotionRecord record = weeklyRecordRepository.saveAndFlush(
			WeeklyDevotionRecord.create(campusId, userId, WEEK_START_DATE)
		);
		List<DevotionDailyCheck> dailyChecks = values.stream()
			.map(value -> DevotionDailyCheck.create(
				record.id(),
				WEEK_START_DATE.plusDays(value.dayOffset()),
				value.quietTimeChecked(),
				value.prayerChecked(),
				value.bibleReadingChecked()
			))
			.toList();
		dailyCheckRepository.saveAllAndFlush(dailyChecks);
		record.updateSummary(dailyChecks, saturdayLateMinutes);
		if (submitted) {
			record.submit(submittedAt);
		}
		return weeklyRecordRepository.saveAndFlush(record);
	}

	private ChargeItem createCharge(
		Long campusId,
		Long userId,
		Long weeklyRecordId,
		int amount,
		ChargeStatus status
	) {
		ChargeItem charge = ChargeItem.create(
			campusId,
			userId,
			PaymentCategory.PENALTY,
			1L,
			"테스트은행",
			"000-0000",
			"테스트",
			ChargeSourceType.DEVOTION_RECORD,
			weeklyRecordId,
			"경건생활 벌금",
			null,
			amount,
			null
		);
		if (status == ChargeStatus.PAID) {
			charge.markPaid(Instant.parse("2026-07-20T01:00:00Z"));
		} else if (status == ChargeStatus.WAIVED) {
			charge.waive();
		} else if (status == ChargeStatus.CANCELED) {
			charge.cancel();
		}
		return chargeItemRepository.saveAndFlush(charge);
	}

	private JsonNode createCampus(String accessToken, String name) throws Exception {
		String body = mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "region": "분당",
					  "description": "%s 설명"
					}
					""".formatted(name, name)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body).path("data");
	}

	private long joinCampus(String accessToken, String inviteCode) throws Exception {
		String body = mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "%s"
					}
					""".formatted(inviteCode)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body).path("data").path("membershipId").asLong();
	}

	private void updateCampusRole(long membershipId, CampusRole campusRole) {
		CampusMember member = campusMemberRepository.findById(membershipId).orElseThrow();
		ReflectionTestUtils.setField(member, "campusRole", campusRole);
		campusMemberRepository.saveAndFlush(member);
	}

	private String signupAndLogin(String email, UserRole role, String name) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(name, email)))
			.andExpect(status().isCreated());

		User user = userRepository.findByEmail(email).orElseThrow();
		ReflectionTestUtils.setField(user, "role", role);
		userRepository.saveAndFlush(user);
		String loginBody = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(loginBody).path("data").path("accessToken").asText();
	}

	private record DailyValue(
		int dayOffset,
		boolean quietTimeChecked,
		boolean prayerChecked,
		boolean bibleReadingChecked
	) {
	}
}
