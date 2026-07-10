package com.faithlog.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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
class AdminDashboardControllerTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private WeeklyDevotionRecordRepository weeklyDevotionRecordRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private PollRepository pollRepository;

	@Autowired
	private PollResponseRepository pollResponseRepository;

	@Test
	void dashboard_summary_allows_service_admin_and_campus_admin_roles_but_rejects_member_other_campus_admin_and_manager_only() throws Exception {
		String serviceAdminToken = signupAndLogin("dashboard-auth-admin@example.com", UserRole.ADMIN, "서비스관리자");
		String creatorToken = signupAndLogin("dashboard-auth-creator@example.com", UserRole.MANAGER, "대상캠생성자");
		String ministerToken = signupAndLogin("dashboard-auth-minister@example.com", UserRole.USER, "전도사");
		String elderToken = signupAndLogin("dashboard-auth-elder@example.com", UserRole.USER, "장로");
		String leaderToken = signupAndLogin("dashboard-auth-leader@example.com", UserRole.USER, "캠장");
		String memberToken = signupAndLogin("dashboard-auth-member@example.com", UserRole.USER, "멤버");
		String otherCampusAdminToken = signupAndLogin("dashboard-auth-other-admin@example.com", UserRole.MANAGER, "다른캠관리자");
		String managerOnlyToken = signupAndLogin("dashboard-auth-manager-only@example.com", UserRole.MANAGER, "전역매니저");
		JsonNode campus = createCampus(creatorToken, "권한캠");
		long campusId = campus.path("campusId").asLong();
		String inviteCode = campus.path("inviteCode").asText();
		updateCampusRole(joinCampus(ministerToken, inviteCode).path("membershipId").asLong(), CampusRole.MINISTER);
		updateCampusRole(joinCampus(elderToken, inviteCode).path("membershipId").asLong(), CampusRole.ELDER);
		updateCampusRole(joinCampus(leaderToken, inviteCode).path("membershipId").asLong(), CampusRole.CAMPUS_LEADER);
		joinCampus(memberToken, inviteCode);
		createCampus(otherCampusAdminToken, "다른권한캠");

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + serviceAdminToken))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + ministerToken))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + elderToken))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + leaderToken))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_DASHBOARD_ACCESS_FORBIDDEN"));
		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + otherCampusAdminToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_DASHBOARD_ACCESS_FORBIDDEN"));
		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + managerOnlyToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_DASHBOARD_ACCESS_FORBIDDEN"));
	}

	@Test
	void dashboard_summary_validates_week_start_date_and_defaults_to_current_monday_in_asia_seoul() throws Exception {
		String managerToken = signupAndLogin("dashboard-week-manager@example.com", UserRole.MANAGER, "주차관리자");
		JsonNode campus = createCampus(managerToken, "주차캠");
		long campusId = campus.path("campusId").asLong();
		LocalDate expectedMonday = LocalDate.now(SEOUL_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate explicitMonday = LocalDate.of(2026, 6, 8);

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.devotion.weekStartDate").value(expectedMonday.toString()));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("weekStartDate", explicitMonday.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.devotion.weekStartDate").value("2026-06-08"));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("weekStartDate", "2026-06-09"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DEVOTION_INVALID_WEEK_START_DATE"));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("weekStartDate", "not-a-date"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void dashboard_summary_returns_member_devotion_charge_and_poll_aggregates() throws Exception {
		String managerToken = signupAndLogin("dashboard-summary-manager@example.com", UserRole.MANAGER, "요약관리자");
		String elderToken = signupAndLogin("dashboard-summary-elder@example.com", UserRole.USER, "요약장로");
		String leaderToken = signupAndLogin("dashboard-summary-leader@example.com", UserRole.USER, "요약캠장");
		String memberToken = signupAndLogin("dashboard-summary-member@example.com", UserRole.USER, "요약멤버");
		String inactiveToken = signupAndLogin("dashboard-summary-inactive@example.com", UserRole.USER, "비활성멤버");
		JsonNode campus = createCampus(managerToken, "요약캠");
		long campusId = campus.path("campusId").asLong();
		String inviteCode = campus.path("inviteCode").asText();
		updateCampusRole(joinCampus(elderToken, inviteCode).path("membershipId").asLong(), CampusRole.ELDER);
		updateCampusRole(joinCampus(leaderToken, inviteCode).path("membershipId").asLong(), CampusRole.CAMPUS_LEADER);
		CampusMember memberMembership = campusMemberRepository.findById(joinCampus(memberToken, inviteCode).path("membershipId").asLong()).orElseThrow();
		CampusMember inactiveMembership = campusMemberRepository.findById(joinCampus(inactiveToken, inviteCode).path("membershipId").asLong()).orElseThrow();
		inactiveMembership.deactivate();
		campusMemberRepository.saveAndFlush(inactiveMembership);
		User manager = userRepository.findByEmail("dashboard-summary-manager@example.com").orElseThrow();
		User elder = userRepository.findByEmail("dashboard-summary-elder@example.com").orElseThrow();
		User leader = userRepository.findByEmail("dashboard-summary-leader@example.com").orElseThrow();
		User member = userRepository.findByEmail("dashboard-summary-member@example.com").orElseThrow();
		LocalDate weekStartDate = LocalDate.of(2026, 6, 8);
		submitWeeklyRecord(campusId, manager.id(), weekStartDate);
		submitWeeklyRecord(campusId, elder.id(), weekStartDate);
		submitWeeklyRecord(campusId, leader.id(), weekStartDate);
		createCharge(campusId, manager.id(), PaymentCategory.PENALTY, ChargeSourceType.DEVOTION_RECORD, 101L, 32000, true);
		createCharge(campusId, elder.id(), PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE, 102L, 22000, true);
		createCharge(campusId, leader.id(), PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE, 103L, 9000, false);
		Poll openPoll1 = createPoll(campusId, "진행 투표 1", PollStatusFixture.OPEN, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
		Poll openPoll2 = createPoll(campusId, "진행 투표 2", PollStatusFixture.OPEN, Instant.now().minusSeconds(60), Instant.now().plusSeconds(7200));
		createPoll(campusId, "최근 종료", PollStatusFixture.CLOSED, Instant.now().minusSeconds(8 * 24 * 60 * 60), Instant.now().minusSeconds(3 * 24 * 60 * 60));
		createPoll(campusId, "오래 전 종료", PollStatusFixture.CLOSED, Instant.now().minusSeconds(10 * 24 * 60 * 60), Instant.now().minusSeconds(8 * 24 * 60 * 60));
		pollResponseRepository.save(PollResponse.create(openPoll1.id(), manager.id(), null));
		pollResponseRepository.save(PollResponse.create(openPoll1.id(), elder.id(), null));
		pollResponseRepository.save(PollResponse.create(openPoll2.id(), member.id(), null));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("weekStartDate", weekStartDate.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campus.campusId").value(campusId))
			.andExpect(jsonPath("$.data.campus.campusName").value("요약캠"))
			.andExpect(jsonPath("$.data.campus.region").value("분당"))
			.andExpect(jsonPath("$.data.members.activeCount").value(4))
			.andExpect(jsonPath("$.data.members.inactiveCount").value(1))
			.andExpect(jsonPath("$.data.members.adminCount").value(3))
			.andExpect(jsonPath("$.data.devotion.weekStartDate").value("2026-06-08"))
			.andExpect(jsonPath("$.data.devotion.submittedCount").value(3))
			.andExpect(jsonPath("$.data.devotion.missingCount").value(1))
			.andExpect(jsonPath("$.data.devotion.submitRate").value(75.0))
			.andExpect(jsonPath("$.data.charges.unpaidAmount").value(54000))
			.andExpect(jsonPath("$.data.charges.unpaidMemberCount").value(2))
			.andExpect(jsonPath("$.data.charges.byCategory[0].paymentCategory").value("PENALTY"))
			.andExpect(jsonPath("$.data.charges.byCategory[0].unpaidAmount").value(32000))
			.andExpect(jsonPath("$.data.charges.byCategory[1].paymentCategory").value("COFFEE"))
			.andExpect(jsonPath("$.data.charges.byCategory[1].unpaidAmount").value(22000))
			.andExpect(jsonPath("$.data.polls.openCount").value(2))
			.andExpect(jsonPath("$.data.polls.recentlyClosedCount").value(1))
			.andExpect(jsonPath("$.data.polls.missingResponseCount").value(5))
			.andExpect(jsonPath("$.data.polls.recentlyClosedDays").value(7));
	}

	private JsonNode createCampus(String accessToken, String name) throws Exception {
		String body = mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "region": "분당",
					  "description": "분당 %s퍼스"
					}
					""".formatted(name, name)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body).path("data");
	}

	private JsonNode joinCampus(String accessToken, String inviteCode) throws Exception {
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
		return objectMapper.readTree(body).path("data");
	}

	private CampusMember updateCampusRole(long membershipId, CampusRole campusRole) {
		CampusMember member = campusMemberRepository.findById(membershipId).orElseThrow();
		ReflectionTestUtils.setField(member, "campusRole", campusRole);
		return campusMemberRepository.saveAndFlush(member);
	}

	private void submitWeeklyRecord(Long campusId, Long userId, LocalDate weekStartDate) {
		WeeklyDevotionRecord record = WeeklyDevotionRecord.create(campusId, userId, weekStartDate);
		record.submit(Instant.now());
		weeklyDevotionRecordRepository.save(record);
	}

	private void createCharge(
		Long campusId,
		Long userId,
		PaymentCategory paymentCategory,
		ChargeSourceType sourceType,
		Long sourceId,
		int amount,
		boolean unpaid
	) {
		ChargeItem chargeItem = ChargeItem.create(
			campusId,
			userId,
			paymentCategory,
			1L,
			"테스트은행",
			"000-0000",
			"테스트",
			sourceType,
			sourceId,
			"테스트 청구",
			null,
			amount,
			null
		);
		if (!unpaid) {
			chargeItem.markPaid(Instant.now());
		}
		chargeItemRepository.saveAndFlush(chargeItem);
	}

	private Poll createPoll(
		Long campusId,
		String title,
		PollStatusFixture status,
		Instant startsAt,
		Instant endsAt
	) {
		Poll poll = Poll.create(
			campusId,
			null,
			title,
			PollType.CUSTOM,
			SelectionType.SINGLE,
			false,
			false,
			ChargeGenerationType.NONE,
			null,
			null,
			startsAt,
			endsAt,
			null
		);
		if (status == PollStatusFixture.OPEN) {
			poll.open();
		}
		if (status == PollStatusFixture.CLOSED) {
			poll.close();
		}
		return pollRepository.save(poll);
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

	private enum PollStatusFixture {
		OPEN,
		CLOSED
	}
}
