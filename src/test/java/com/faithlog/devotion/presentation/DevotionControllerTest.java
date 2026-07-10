package com.faithlog.devotion.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.billing.service.BillingService;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.devotion.domain.PenaltyCalculationType;
import com.faithlog.devotion.domain.PenaltyRule;
import com.faithlog.devotion.domain.PenaltyRuleType;
import com.faithlog.devotion.infrastructure.jpa.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.jpa.PenaltyRuleRepository;
import com.faithlog.devotion.infrastructure.jpa.WeeklyDevotionRecordRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
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
class DevotionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BillingService billingService;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private WeeklyDevotionRecordRepository weeklyRecordRepository;

	@Autowired
	private DevotionDailyCheckRepository dailyCheckRepository;

	@Autowired
	private PenaltyRuleRepository penaltyRuleRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Test
	void my_monthly_summary_api_returns_current_users_monthly_devotion_statistics() throws Exception {
		String managerToken = signupAndLogin("devotion-http-monthly-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "78캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("devotion-http-monthly-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("devotion-http-monthly-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}", campusId, "2026-06-29")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "dailyChecks": [
					    {
					      "recordDate": "2026-06-29",
					      "quietTimeChecked": true,
					      "prayerChecked": true,
					      "bibleReadingChecked": false
					    },
					    {
					      "recordDate": "2026-06-30",
					      "quietTimeChecked": true,
					      "prayerChecked": false,
					      "bibleReadingChecked": false
					    },
					    {
					      "recordDate": "2026-07-01",
					      "quietTimeChecked": true,
					      "prayerChecked": true,
					      "bibleReadingChecked": true
					    }
					  ],
					  "saturdayLateMinutes": 8,
					  "submit": false
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/monthly-summary", campusId)
				.param("year", "2026")
				.param("month", "6")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.campusId").value(campusId))
			.andExpect(jsonPath("$.data.campusName").value("78캠"))
			.andExpect(jsonPath("$.data.region").value("분당"))
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.name").value("경건HTTP"))
			.andExpect(jsonPath("$.data.year").value(2026))
			.andExpect(jsonPath("$.data.month").value(6))
			.andExpect(jsonPath("$.data.devotion.quietTimeCount").value(2))
			.andExpect(jsonPath("$.data.devotion.prayerCount").value(1))
			.andExpect(jsonPath("$.data.devotion.bibleReadingCount").value(0))
			.andExpect(jsonPath("$.data.devotion.saturdayLateMinutes").value(0))
			.andExpect(jsonPath("$.data.weeklyRecords.length()").value(1))
			.andExpect(jsonPath("$.data.weeklyRecords[0].weeklyRecordId").isNumber())
			.andExpect(jsonPath("$.data.weeklyRecords[0].weekStartDate").value("2026-06-29"))
			.andExpect(jsonPath("$.data.weeklyRecords[0].weekEndDate").value("2026-07-05"))
			.andExpect(jsonPath("$.data.weeklyRecords[0].quietTimeCount").value(2))
			.andExpect(jsonPath("$.data.weeklyRecords[0].prayerCount").value(1))
			.andExpect(jsonPath("$.data.weeklyRecords[0].bibleReadingCount").value(0))
			.andExpect(jsonPath("$.data.weeklyRecords[0].saturdayLateMinutes").value(0))
			.andExpect(jsonPath("$.data.weeklyRecords[0].submittedAt").doesNotExist());
	}

	@Test
	void my_monthly_summary_api_rejects_invalid_month_and_non_member() throws Exception {
		String managerToken = signupAndLogin("devotion-http-monthly-invalid-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "79캠");
		long campusId = campus.path("campusId").asLong();
		String outsiderToken = signupAndLogin("devotion-http-monthly-outsider@example.com", UserRole.USER);

		mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/monthly-summary", campusId)
				.param("year", "2026")
				.param("month", "13")
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("DEVOTION_INVALID_YEAR_MONTH"))
			.andExpect(jsonPath("$.message").value("조회 연월이 올바르지 않습니다."));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/monthly-summary", campusId)
				.param("year", "0")
				.param("month", "6")
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("DEVOTION_INVALID_YEAR_MONTH"))
			.andExpect(jsonPath("$.message").value("조회 연월이 올바르지 않습니다."));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/monthly-summary", campusId)
				.param("year", "2026")
				.param("month", "6")
				.header("Authorization", "Bearer " + outsiderToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("DEVOTION_ACCESS_FORBIDDEN"));
	}

	@Test
	void daily_and_weekly_devotion_apis_create_update_and_read_my_week() throws Exception {
		String managerToken = signupAndLogin("devotion-http-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("devotion-http-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "70캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("devotion-http-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("devotion-http-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyPrerequisites(campusId, manager.id(), "123-456789-201");
		long chargeCountBeforeDailyCheck = chargeItemRepository.count();

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/days/{recordDate}", campusId, "2026-06-17")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "quietTimeChecked": true,
					  "prayerChecked": true,
					  "bibleReadingChecked": false
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.weeklyRecordId").isNumber())
			.andExpect(jsonPath("$.data.recordDate").value("2026-06-17"))
			.andExpect(jsonPath("$.data.quietTimeChecked").value(true))
			.andExpect(jsonPath("$.data.prayerChecked").value(true))
			.andExpect(jsonPath("$.data.bibleReadingChecked").value(false))
			.andExpect(jsonPath("$.data.quietTimeCount").value(1))
			.andExpect(jsonPath("$.data.prayerCount").value(1))
			.andExpect(jsonPath("$.data.bibleReadingCount").value(0))
			.andExpect(jsonPath("$.data.submittedAt").doesNotExist());

		assertThat(weeklyRecordRepository.findByCampusIdAndUserIdAndWeekStartDate(
			campusId,
			member.id(),
			java.time.LocalDate.of(2026, 6, 15)
		)).isPresent();
		assertThat(chargeItemRepository.count()).isEqualTo(chargeCountBeforeDailyCheck);

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}", campusId, "2026-06-15")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "dailyChecks": [
					    {
					      "recordDate": "2026-06-15",
					      "quietTimeChecked": true,
					      "prayerChecked": true,
					      "bibleReadingChecked": true
					    },
					    {
					      "recordDate": "2026-06-17",
					      "quietTimeChecked": false,
					      "prayerChecked": true,
					      "bibleReadingChecked": false
					    }
					  ],
					  "saturdayLateMinutes": 0,
					  "submit": true
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.weekStartDate").value("2026-06-15"))
			.andExpect(jsonPath("$.data.weekEndDate").value("2026-06-21"))
			.andExpect(jsonPath("$.data.quietTimeCount").value(1))
			.andExpect(jsonPath("$.data.prayerCount").value(2))
			.andExpect(jsonPath("$.data.bibleReadingCount").value(1))
			.andExpect(jsonPath("$.data.saturdayLateMinutes").value(0))
			.andExpect(jsonPath("$.data.submittedAt").isString())
			.andExpect(jsonPath("$.data.generatedCharges").doesNotExist())
			.andExpect(jsonPath("$.data.dailyChecks.length()").value(7));

		long weeklyRecordId = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(campusId, member.id(), java.time.LocalDate.of(2026, 6, 15))
			.orElseThrow()
			.id();
		assertThat(dailyCheckRepository.findByWeeklyRecordIdOrderByRecordDateAsc(weeklyRecordId)).hasSize(7);
		assertThat(chargeItemRepository.count()).isEqualTo(chargeCountBeforeDailyCheck + 1);

		mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}", campusId, "2026-06-15")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campusId").value(campusId))
			.andExpect(jsonPath("$.data.campusName").value("70캠"))
			.andExpect(jsonPath("$.data.region").value("분당"))
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.weekStartDate").value("2026-06-15"))
			.andExpect(jsonPath("$.data.dailyChecks.length()").value(7));
	}

	@Test
	void weekly_api_rejects_non_monday_weekStartDate() throws Exception {
		String managerToken = signupAndLogin("devotion-http-invalid-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "71캠");

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}",
				campus.path("campusId").asLong(),
				"2026-06-16")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "dailyChecks": [],
					  "saturdayLateMinutes": 0,
					  "submit": true
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("DEVOTION_INVALID_WEEK_START_DATE"))
			.andExpect(jsonPath("$.message").value("weekStartDate는 월요일이어야 합니다."));
	}

	@Test
	void weekly_api_rejects_daily_check_recordDate_outside_week() throws Exception {
		String managerToken = signupAndLogin("devotion-http-out-of-week-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "73캠");

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}",
				campus.path("campusId").asLong(),
				"2026-06-15")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "dailyChecks": [
					    {
					      "recordDate": "2026-06-22",
					      "quietTimeChecked": true,
					      "prayerChecked": true,
					      "bibleReadingChecked": true
					    }
					  ],
					  "saturdayLateMinutes": 0,
					  "submit": true
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("DEVOTION_DAILY_CHECK_DATE_OUT_OF_WEEK"))
			.andExpect(jsonPath("$.message").value("dailyChecks[].recordDate는 요청 주차 안의 날짜여야 합니다."));
	}

	@Test
	void weekly_api_rejects_negative_saturday_late_minutes() throws Exception {
		String managerToken = signupAndLogin("devotion-http-negative-late-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "74캠");

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}",
				campus.path("campusId").asLong(),
				"2026-06-15")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "dailyChecks": [
					    {
					      "recordDate": "2026-06-15",
					      "quietTimeChecked": true,
					      "prayerChecked": true,
					      "bibleReadingChecked": true
					    }
					  ],
					  "saturdayLateMinutes": -1,
					  "submit": true
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("DEVOTION_INVALID_SATURDAY_LATE_MINUTES"))
			.andExpect(jsonPath("$.message").value("saturdayLateMinutes는 0 이상이어야 합니다."));
	}

	@Test
	void daily_api_rejects_same_week_change_after_weekly_record_was_submitted() throws Exception {
		String managerToken = signupAndLogin("devotion-http-daily-after-submit-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("devotion-http-daily-after-submit-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "77캠");
		long campusId = campus.path("campusId").asLong();
		createPenaltyPrerequisites(campusId, manager.id(), "123-456789-203");
		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}", campusId, "2026-06-15")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "dailyChecks": [
					    {
					      "recordDate": "2026-06-17",
					      "quietTimeChecked": true,
					      "prayerChecked": true,
					      "bibleReadingChecked": true
					    }
					  ],
					  "saturdayLateMinutes": 0,
					  "submit": true
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/days/{recordDate}", campusId, "2026-06-17")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "quietTimeChecked": false,
					  "prayerChecked": false,
					  "bibleReadingChecked": false
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("DEVOTION_WEEKLY_ALREADY_SUBMITTED"))
			.andExpect(jsonPath("$.message").value("이미 제출된 주간 경건생활은 수정할 수 없습니다."));
	}

	@Test
	void my_week_api_returns_default_week_when_record_is_missing() throws Exception {
		String managerToken = signupAndLogin("devotion-http-empty-week-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "75캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("devotion-http-empty-week-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("devotion-http-empty-week-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		long weeklyRecordCountBefore = weeklyRecordRepository.count();
		long dailyCheckCountBefore = dailyCheckRepository.count();

		String body = mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}",
				campusId,
				"2026-06-15")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.weekStartDate").value("2026-06-15"))
			.andExpect(jsonPath("$.data.weekEndDate").value("2026-06-21"))
			.andExpect(jsonPath("$.data.quietTimeCount").value(0))
			.andExpect(jsonPath("$.data.prayerCount").value(0))
			.andExpect(jsonPath("$.data.bibleReadingCount").value(0))
			.andExpect(jsonPath("$.data.saturdayLateMinutes").value(0))
			.andExpect(jsonPath("$.data.dailyChecks.length()").value(7))
			.andExpect(jsonPath("$.data.dailyChecks[0].recordDate").value("2026-06-15"))
			.andExpect(jsonPath("$.data.dailyChecks[6].recordDate").value("2026-06-21"))
			.andExpect(jsonPath("$.data.dailyChecks[0].quietTimeChecked").value(false))
			.andExpect(jsonPath("$.data.dailyChecks[0].prayerChecked").value(false))
			.andExpect(jsonPath("$.data.dailyChecks[0].bibleReadingChecked").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode data = objectMapper.readTree(body).path("data");
		assertThat(data.has("weeklyRecordId")).isTrue();
		assertThat(data.path("weeklyRecordId").isNull()).isTrue();
		assertThat(data.has("submittedAt")).isTrue();
		assertThat(data.path("submittedAt").isNull()).isTrue();
		assertThat(data.path("dailyChecks").get(0).has("id")).isTrue();
		assertThat(data.path("dailyChecks").get(0).path("id").isNull()).isTrue();
		assertThat(weeklyRecordRepository.findByCampusIdAndUserIdAndWeekStartDate(
			campusId,
			member.id(),
			java.time.LocalDate.of(2026, 6, 15)
		)).isEmpty();
		assertThat(weeklyRecordRepository.count()).isEqualTo(weeklyRecordCountBefore);
		assertThat(dailyCheckRepository.count()).isEqualTo(dailyCheckCountBefore);
	}

	@Test
	void my_week_api_returns_seven_days_when_only_one_daily_check_exists() throws Exception {
		String managerToken = signupAndLogin("devotion-http-partial-week-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "76캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("devotion-http-partial-week-member@example.com", UserRole.USER);
		joinCampus(memberToken, campus.path("inviteCode").asText());
		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/days/{recordDate}", campusId, "2026-06-17")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "quietTimeChecked": true,
					  "prayerChecked": true,
					  "bibleReadingChecked": false
					}
					"""))
			.andExpect(status().isOk());
		long dailyCheckCountBefore = dailyCheckRepository.count();

		String body = mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}",
				campusId,
				"2026-06-15")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.dailyChecks.length()").value(7))
			.andExpect(jsonPath("$.data.dailyChecks[0].recordDate").value("2026-06-15"))
			.andExpect(jsonPath("$.data.dailyChecks[1].recordDate").value("2026-06-16"))
			.andExpect(jsonPath("$.data.dailyChecks[2].recordDate").value("2026-06-17"))
			.andExpect(jsonPath("$.data.dailyChecks[6].recordDate").value("2026-06-21"))
			.andExpect(jsonPath("$.data.dailyChecks[2].quietTimeChecked").value(true))
			.andExpect(jsonPath("$.data.dailyChecks[2].prayerChecked").value(true))
			.andExpect(jsonPath("$.data.dailyChecks[2].bibleReadingChecked").value(false))
			.andExpect(jsonPath("$.data.dailyChecks[0].quietTimeChecked").value(false))
			.andExpect(jsonPath("$.data.dailyChecks[0].prayerChecked").value(false))
			.andExpect(jsonPath("$.data.dailyChecks[0].bibleReadingChecked").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode dailyChecks = objectMapper.readTree(body).path("data").path("dailyChecks");
		assertThat(dailyChecks.get(2).path("id").isNumber()).isTrue();
		assertThat(dailyChecks.get(0).path("id").isNull()).isTrue();
		assertThat(dailyCheckRepository.count()).isEqualTo(dailyCheckCountBefore);
	}

	@Test
	void admin_missing_uses_submitted_at_and_requires_campus_manager() throws Exception {
		String managerToken = signupAndLogin("devotion-http-missing-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("devotion-http-missing-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "72캠");
		long campusId = campus.path("campusId").asLong();
		String submittedToken = signupAndLogin("devotion-http-submitted@example.com", UserRole.USER);
		User submitted = userRepository.findByEmail("devotion-http-submitted@example.com").orElseThrow();
		joinCampus(submittedToken, campus.path("inviteCode").asText());
		String unsubmittedToken = signupAndLogin("devotion-http-unsubmitted@example.com", UserRole.USER);
		User unsubmitted = userRepository.findByEmail("devotion-http-unsubmitted@example.com").orElseThrow();
		joinCampus(unsubmittedToken, campus.path("inviteCode").asText());
		String normalToken = signupAndLogin("devotion-http-normal@example.com", UserRole.USER);
		joinCampus(normalToken, campus.path("inviteCode").asText());
		createPenaltyPrerequisites(campusId, manager.id(), "123-456789-202");

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}", campusId, "2026-06-15")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "dailyChecks": [
					    {
					      "recordDate": "2026-06-15",
					      "quietTimeChecked": true,
					      "prayerChecked": true,
					      "bibleReadingChecked": true
					    }
					  ],
					  "saturdayLateMinutes": 0,
					  "submit": true
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}", campusId, "2026-06-15")
				.header("Authorization", "Bearer " + submittedToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "dailyChecks": [
					    {
					      "recordDate": "2026-06-15",
					      "quietTimeChecked": true,
					      "prayerChecked": true,
					      "bibleReadingChecked": true
					    }
					  ],
					  "saturdayLateMinutes": 0,
					  "submit": true
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/days/{recordDate}", campusId, "2026-06-16")
				.header("Authorization", "Bearer " + unsubmittedToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "quietTimeChecked": true,
					  "prayerChecked": true,
					  "bibleReadingChecked": true
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/devotions/missing", campusId)
				.param("weekStartDate", "2026-06-15")
				.header("Authorization", "Bearer " + normalToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("DEVOTION_ADMIN_FORBIDDEN"));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/devotions/missing", campusId)
				.param("weekStartDate", "2026-06-15")
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].userId").value(unsubmitted.id()))
			.andExpect(jsonPath("$.data[0].name").value("경건HTTP"))
			.andExpect(jsonPath("$.data[0].email").value("devotion-http-unsubmitted@example.com"))
			.andExpect(jsonPath("$.data[0].campusMemberId").isNumber())
			.andExpect(jsonPath("$.data[0].campusName").value("72캠"))
			.andExpect(jsonPath("$.data[0].region").value("분당"));
	}

	private JsonNode createCampus(String accessToken, String name) throws Exception {
		String body = mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "region": "분당",
					  "description": "분당 %s"
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

	private void createPenaltyPrerequisites(long campusId, long managerId, String accountNumber) {
		penaltyRuleRepository.saveAllAndFlush(java.util.List.of(
			PenaltyRule.create(campusId, PenaltyRuleType.QUIET_TIME, PenaltyCalculationType.MISSING_COUNT, 5, 0, 500),
			PenaltyRule.create(campusId, PenaltyRuleType.PRAYER, PenaltyCalculationType.MISSING_COUNT, 5, 0, 500),
			PenaltyRule.create(campusId, PenaltyRuleType.BIBLE_READING, PenaltyCalculationType.MISSING_COUNT, 5, 0, 300),
			PenaltyRule.create(campusId, PenaltyRuleType.SATURDAY_LATE, PenaltyCalculationType.LATE_MINUTE, 0, 1000, 100)
		));
		billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId,
			managerId,
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"하나은행",
			accountNumber,
			"벌금회계",
			null
		));
	}

	private String signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "경건HTTP",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
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

	private void updateCampusRole(long campusId, long userId, CampusRole campusRole) {
		CampusMember member = campusMemberRepository.findByCampusIdAndUserId(campusId, userId).orElseThrow();
		ReflectionTestUtils.setField(member, "campusRole", campusRole);
		campusMemberRepository.saveAndFlush(member);
	}
}
