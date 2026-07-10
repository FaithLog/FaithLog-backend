package com.faithlog.devotion.controller;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
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
import com.faithlog.devotion.domain.type.PenaltyCalculationType;
import com.faithlog.devotion.domain.entity.PenaltyRule;
import com.faithlog.devotion.domain.type.PenaltyRuleType;
import com.faithlog.devotion.infrastructure.repository.PenaltyRuleRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@ActiveProfiles("test")
class DevotionApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PenaltyRuleRepository penaltyRuleRepository;

	@Autowired
	private BillingService billingService;

	@Test
	void documents_devotion_my_monthly_summary_success() throws Exception {
		String managerToken = signupAndLogin("docs-devotion-monthly-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "89캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-devotion-monthly-member@example.com", UserRole.USER);
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
			.andExpect(jsonPath("$.data.devotion.quietTimeCount").value(2))
			.andExpect(jsonPath("$.data.devotion.saturdayLateMinutes").value(0))
			.andExpect(jsonPath("$.data.weeklyRecords.length()").value(1))
			.andDo(document("devotion-my-monthly-summary-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("조회할 캠퍼스 ID")),
				queryParameters(
					parameterWithName("year").description("조회 연도. 1 이상 허용"),
					parameterWithName("month").description("조회 월. 1부터 12까지 허용")
				),
				responseFields(apiResponseFields(monthlySummaryResponseFields()))
			));
	}

	@Test
	void documents_devotion_invalid_year_month() throws Exception {
		String managerToken = signupAndLogin("docs-devotion-monthly-invalid-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "90캠");

		mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/monthly-summary", campus.path("campusId").asLong())
				.param("year", "0")
				.param("month", "6")
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DEVOTION_INVALID_YEAR_MONTH"))
			.andDo(document("devotion-invalid-year-month",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				queryParameters(
					parameterWithName("year").description("잘못된 조회 연도 예시. 1 이상 허용"),
					parameterWithName("month").description("조회 월. 1부터 12까지 허용")
				),
				responseFields(errorResponseFields())
			));
	}

	@Test
	void documents_devotion_daily_weekly_my_week_and_admin_missing_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-devotion-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-devotion-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "80캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-devotion-member@example.com", UserRole.USER);
		joinCampus(memberToken, campus.path("inviteCode").asText());
		String missingToken = signupAndLogin("docs-devotion-missing@example.com", UserRole.USER);
		joinCampus(missingToken, campus.path("inviteCode").asText());
		createPenaltyPrerequisites(campusId, manager.id(), "123-456789-301");

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
			.andExpect(jsonPath("$.data.submittedAt").doesNotExist())
			.andDo(document("devotion-daily-check-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("경건생활 체크 대상 캠퍼스 ID"),
					parameterWithName("recordDate").description("체크할 날짜. `yyyy-MM-dd`")
				),
				requestFields(dailyCheckRequestFields()),
				responseFields(apiResponseFields(
					fieldWithPath("data.weeklyRecordId").description("동기화된 주간 경건생활 기록 ID"),
					fieldWithPath("data.recordDate").description("체크 날짜"),
					fieldWithPath("data.quietTimeChecked").description("큐티 체크 여부"),
					fieldWithPath("data.prayerChecked").description("기도 체크 여부"),
					fieldWithPath("data.bibleReadingChecked").description("말씀 읽기 체크 여부"),
					fieldWithPath("data.quietTimeCount").description("해당 주차 큐티 체크 수"),
					fieldWithPath("data.prayerCount").description("해당 주차 기도 체크 수"),
					fieldWithPath("data.bibleReadingCount").description("해당 주차 말씀 읽기 체크 수"),
					fieldWithPath("data.submittedAt").type(JsonFieldType.STRING).optional()
						.description("주간 제출 시각. 하루 체크만으로는 생성되지 않음")
				))
			));

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
			.andExpect(jsonPath("$.data.dailyChecks.length()").value(7))
			.andExpect(jsonPath("$.data.generatedCharges").doesNotExist())
			.andDo(document("devotion-weekly-save-submit-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("경건생활 제출 대상 캠퍼스 ID"),
					parameterWithName("weekStartDate").description("주 시작일. 월요일만 허용")
				),
				requestFields(
					fieldWithPath("dailyChecks").description("일별 체크 목록. 요청에 없는 주간 날짜는 제출 시 false로 채움"),
					fieldWithPath("dailyChecks[].recordDate").description("일별 체크 날짜"),
					fieldWithPath("dailyChecks[].quietTimeChecked").description("큐티 체크 여부"),
					fieldWithPath("dailyChecks[].prayerChecked").description("기도 체크 여부"),
					fieldWithPath("dailyChecks[].bibleReadingChecked").description("말씀 읽기 체크 여부"),
					fieldWithPath("saturdayLateMinutes").description("토요 목자모임 지각 시간(분)"),
					fieldWithPath("submit").description("true이면 제출 처리하고 submittedAt과 주간 요약을 갱신")
				),
				responseFields(apiResponseFields(weeklyResponseFields()))
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}", campusId, "2026-06-15")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").isNumber())
			.andDo(document("devotion-my-week-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("조회할 캠퍼스 ID"),
					parameterWithName("weekStartDate").description("조회할 주 시작일. 월요일만 허용")
				),
				responseFields(apiResponseFields(weeklyResponseFields()))
			));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/devotions/missing", campusId)
				.param("weekStartDate", "2026-06-15")
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andDo(document("devotion-admin-missing-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("미제출자를 조회할 캠퍼스 ID")),
				queryParameters(parameterWithName("weekStartDate").description("조회할 주 시작일. 월요일만 허용")),
				responseFields(apiResponseFields(
					fieldWithPath("data[]").description("주간 제출 기록이 없거나 submittedAt이 null인 ACTIVE 멤버 목록"),
					fieldWithPath("data[].userId").description("미제출 사용자 ID"),
					fieldWithPath("data[].name").description("미제출 사용자 이름"),
					fieldWithPath("data[].email").description("미제출 사용자 이메일"),
					fieldWithPath("data[].campusMemberId").description("캠퍼스 멤버십 ID"),
					fieldWithPath("data[].campusName").description("캠퍼스 이름"),
					fieldWithPath("data[].region").optional().description("캠퍼스 지역")
				))
			));
	}

	@Test
	void documents_devotion_my_week_default_success() throws Exception {
		String managerToken = signupAndLogin("docs-devotion-empty-week-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "84캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-devotion-empty-week-member@example.com", UserRole.USER);
		joinCampus(memberToken, campus.path("inviteCode").asText());

		String body = mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}",
				campusId,
				"2026-06-15")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.weekStartDate").value("2026-06-15"))
			.andExpect(jsonPath("$.data.dailyChecks.length()").value(7))
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode data = objectMapper.readTree(body).path("data");
		org.assertj.core.api.Assertions.assertThat(data.has("weeklyRecordId")).isTrue();
		org.assertj.core.api.Assertions.assertThat(data.path("weeklyRecordId").isNull()).isTrue();
		org.assertj.core.api.Assertions.assertThat(data.has("submittedAt")).isTrue();
		org.assertj.core.api.Assertions.assertThat(data.path("submittedAt").isNull()).isTrue();
		org.assertj.core.api.Assertions.assertThat(data.path("dailyChecks").get(0).has("id")).isTrue();
		org.assertj.core.api.Assertions.assertThat(data.path("dailyChecks").get(0).path("id").isNull()).isTrue();

		mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}",
				campusId,
				"2026-06-15")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andDo(document("devotion-my-week-default-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("조회할 캠퍼스 ID"),
					parameterWithName("weekStartDate").description("조회할 주 시작일. 월요일만 허용")
				),
				responseFields(apiResponseFields(weeklyResponseFields()))
			));
	}

	@Test
	void documents_devotion_my_week_partial_success() throws Exception {
		String managerToken = signupAndLogin("docs-devotion-partial-week-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "85캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-devotion-partial-week-member@example.com", UserRole.USER);
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

		String body = mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}",
				campusId,
				"2026-06-15")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.dailyChecks.length()").value(7))
			.andExpect(jsonPath("$.data.dailyChecks[2].recordDate").value("2026-06-17"))
			.andExpect(jsonPath("$.data.dailyChecks[2].quietTimeChecked").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode dailyChecks = objectMapper.readTree(body).path("data").path("dailyChecks");
		org.assertj.core.api.Assertions.assertThat(dailyChecks.get(2).path("id").isNumber()).isTrue();
		org.assertj.core.api.Assertions.assertThat(dailyChecks.get(0).path("id").isNull()).isTrue();

		mockMvc.perform(get("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}",
				campusId,
				"2026-06-15")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andDo(document("devotion-my-week-partial-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("조회할 캠퍼스 ID"),
					parameterWithName("weekStartDate").description("조회할 주 시작일. 월요일만 허용")
				),
				responseFields(apiResponseFields(weeklyResponseFields()))
			));
	}

	@Test
	void documents_devotion_invalid_week_start_date() throws Exception {
		String managerToken = signupAndLogin("docs-devotion-invalid-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "81캠");

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
			.andExpect(jsonPath("$.code").value("DEVOTION_INVALID_WEEK_START_DATE"))
			.andDo(document("devotion-invalid-week-start-date",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("월요일이 아닌 주 시작일 예시")
				),
				requestFields(
					fieldWithPath("dailyChecks").description("일별 체크 목록"),
					fieldWithPath("saturdayLateMinutes").description("토요 목자모임 지각 시간(분)"),
					fieldWithPath("submit").description("제출 여부")
				),
				responseFields(errorResponseFields())
			));
	}

	@Test
	void documents_devotion_daily_check_date_out_of_week() throws Exception {
		String managerToken = signupAndLogin("docs-devotion-out-of-week-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "82캠");

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
			.andExpect(jsonPath("$.code").value("DEVOTION_DAILY_CHECK_DATE_OUT_OF_WEEK"))
			.andDo(document("devotion-daily-check-date-out-of-week",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("주 시작일. 이 날짜부터 7일 안의 dailyChecks만 허용")
				),
				requestFields(weeklyRequestFields()),
				responseFields(errorResponseFields())
			));
	}

	@Test
	void documents_devotion_invalid_saturday_late_minutes() throws Exception {
		String managerToken = signupAndLogin("docs-devotion-negative-late-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "83캠");

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
			.andExpect(jsonPath("$.code").value("DEVOTION_INVALID_SATURDAY_LATE_MINUTES"))
			.andDo(document("devotion-invalid-saturday-late-minutes",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("주 시작일")
				),
				requestFields(weeklyRequestFields()),
				responseFields(errorResponseFields())
			));
	}

	@Test
		void documents_devotion_missing_penalty_account() throws Exception {
			String managerToken = signupAndLogin("docs-devotion-no-account-manager@example.com", UserRole.MANAGER);
			JsonNode campus = createCampus(managerToken, "86캠");
			createPenaltyRules(campus.path("campusId").asLong());

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
					  "saturdayLateMinutes": 0,
					  "submit": true
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING"))
			.andExpect(jsonPath("$.message").value("관리자에게 문의하세요"))
			.andDo(document("devotion-missing-penalty-account",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("주 시작일")
				),
				requestFields(weeklyRequestFields()),
				responseFields(errorResponseFields())
			));
	}

	@Test
	void documents_devotion_weekly_already_submitted() throws Exception {
		String managerToken = signupAndLogin("docs-devotion-duplicate-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-devotion-duplicate-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "87캠");
		long campusId = campus.path("campusId").asLong();
		createPenaltyPrerequisites(campusId, manager.id(), "123-456789-302");
		String weeklyBody = """
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
			""";
		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}", campusId, "2026-06-15")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(weeklyBody))
			.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}", campusId, "2026-06-15")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(weeklyBody))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEVOTION_WEEKLY_ALREADY_SUBMITTED"))
			.andExpect(jsonPath("$.message").value("이미 제출된 주간 경건생활은 수정할 수 없습니다."))
			.andDo(document("devotion-weekly-already-submitted",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("이미 제출된 주 시작일")
				),
				requestFields(weeklyRequestFields()),
				responseFields(errorResponseFields())
			));
	}

	@Test
	void documents_devotion_daily_check_already_submitted_week() throws Exception {
		String managerToken = signupAndLogin("docs-devotion-daily-after-submit-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-devotion-daily-after-submit-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "88캠");
		long campusId = campus.path("campusId").asLong();
		createPenaltyPrerequisites(campusId, manager.id(), "123-456789-303");
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
			.andExpect(jsonPath("$.code").value("DEVOTION_WEEKLY_ALREADY_SUBMITTED"))
			.andExpect(jsonPath("$.message").value("이미 제출된 주간 경건생활은 수정할 수 없습니다."))
			.andDo(document("devotion-daily-check-already-submitted-week",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("recordDate").description("이미 최종 제출된 주차에 속한 일별 체크 날짜")
				),
				requestFields(dailyCheckRequestFields()),
				responseFields(errorResponseFields())
			));
	}

	private FieldDescriptor[] dailyCheckRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("quietTimeChecked").description("큐티 체크 여부"),
			fieldWithPath("prayerChecked").description("기도 체크 여부"),
			fieldWithPath("bibleReadingChecked").description("말씀 읽기 체크 여부")
		};
	}

	private FieldDescriptor[] weeklyRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("dailyChecks").description("일별 체크 목록"),
			fieldWithPath("dailyChecks[].recordDate").description("일별 체크 날짜. weekStartDate부터 6일 뒤까지만 허용"),
			fieldWithPath("dailyChecks[].quietTimeChecked").description("큐티 체크 여부"),
			fieldWithPath("dailyChecks[].prayerChecked").description("기도 체크 여부"),
			fieldWithPath("dailyChecks[].bibleReadingChecked").description("말씀 읽기 체크 여부"),
			fieldWithPath("saturdayLateMinutes").description("토요 목자모임 지각 시간(분). 0 이상만 허용"),
			fieldWithPath("submit").description("제출 여부")
		};
	}

	private FieldDescriptor[] weeklyResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("data.weeklyRecordId").description("주간 경건생활 기록 ID. 아직 DB row가 없는 기본 조회 응답에서는 null"),
			fieldWithPath("data.campusId").description("캠퍼스 ID"),
			fieldWithPath("data.campusName").description("캠퍼스 이름"),
			fieldWithPath("data.region").optional().description("캠퍼스 지역"),
			fieldWithPath("data.userId").description("기록 소유 사용자 ID"),
			fieldWithPath("data.weekStartDate").description("주 시작일"),
			fieldWithPath("data.weekEndDate").description("주 종료일"),
			fieldWithPath("data.quietTimeCount").description("주간 큐티 체크 수"),
			fieldWithPath("data.prayerCount").description("주간 기도 체크 수"),
			fieldWithPath("data.bibleReadingCount").description("주간 말씀 읽기 체크 수"),
			fieldWithPath("data.saturdayLateMinutes").description("토요 목자모임 지각 시간(분)"),
			fieldWithPath("data.submittedAt").description("제출 시각. 미제출 또는 기본 조회 응답에서는 null"),
			fieldWithPath("data.dailyChecks").description("월요일부터 일요일까지 7일치 일별 체크. 저장된 row가 없는 날짜도 false 기본값으로 포함"),
			fieldWithPath("data.dailyChecks[].id").optional().description("일별 체크 ID. 해당 날짜 DB row가 없으면 null"),
			fieldWithPath("data.dailyChecks[].recordDate").description("체크 날짜"),
			fieldWithPath("data.dailyChecks[].quietTimeChecked").description("큐티 체크 여부"),
			fieldWithPath("data.dailyChecks[].prayerChecked").description("기도 체크 여부"),
			fieldWithPath("data.dailyChecks[].bibleReadingChecked").description("말씀 읽기 체크 여부")
		};
	}

	private FieldDescriptor[] monthlySummaryResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("data.campusId").description("캠퍼스 ID"),
			fieldWithPath("data.campusName").description("캠퍼스 이름"),
			fieldWithPath("data.region").optional().description("캠퍼스 지역"),
			fieldWithPath("data.userId").description("현재 로그인한 사용자 ID"),
			fieldWithPath("data.name").description("현재 로그인한 사용자 이름"),
			fieldWithPath("data.year").description("조회 연도"),
			fieldWithPath("data.month").description("조회 월"),
			fieldWithPath("data.devotion").description("선택 월의 경건생활 합계"),
			fieldWithPath("data.devotion.quietTimeCount").description("선택 월 recordDate 기준 큐티 체크 수"),
			fieldWithPath("data.devotion.prayerCount").description("선택 월 recordDate 기준 기도 체크 수"),
			fieldWithPath("data.devotion.bibleReadingCount").description("선택 월 recordDate 기준 말씀 읽기 체크 수"),
			fieldWithPath("data.devotion.saturdayLateMinutes")
				.description("선택 월에 토요일 날짜가 포함되는 주간 기록의 토요 목자모임 지각 시간 합계"),
			fieldWithPath("data.weeklyRecords").description("선택 월에 포함되는 일별 체크를 주차별로 묶은 목록"),
			fieldWithPath("data.weeklyRecords[].weeklyRecordId").description("주간 경건생활 기록 ID"),
			fieldWithPath("data.weeklyRecords[].weekStartDate").description("주 시작일"),
			fieldWithPath("data.weeklyRecords[].weekEndDate").description("주 종료일"),
			fieldWithPath("data.weeklyRecords[].quietTimeCount").description("선택 월 날짜만 반영한 해당 주차 큐티 체크 수"),
			fieldWithPath("data.weeklyRecords[].prayerCount").description("선택 월 날짜만 반영한 해당 주차 기도 체크 수"),
			fieldWithPath("data.weeklyRecords[].bibleReadingCount").description("선택 월 날짜만 반영한 해당 주차 말씀 읽기 체크 수"),
			fieldWithPath("data.weeklyRecords[].saturdayLateMinutes")
				.description("해당 주차 토요일 날짜가 선택 월에 포함될 때만 반영되는 지각 시간"),
			fieldWithPath("data.weeklyRecords[].submittedAt").description("주간 제출 시각. 미제출이면 null")
		};
	}

	private FieldDescriptor[] apiResponseFields(FieldDescriptor... dataFields) {
		FieldDescriptor[] commonFields = new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부"),
			fieldWithPath("code").description("응답 코드"),
			fieldWithPath("message").description("응답 메시지"),
			fieldWithPath("data").description("응답 데이터"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
		FieldDescriptor[] fields = new FieldDescriptor[commonFields.length + dataFields.length];
		System.arraycopy(commonFields, 0, fields, 0, commonFields.length);
		System.arraycopy(dataFields, 0, fields, commonFields.length, dataFields.length);
		return fields;
	}

	private FieldDescriptor[] errorResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 실패 시 false"),
			fieldWithPath("code").description("상세 에러 코드"),
			fieldWithPath("message").description("사용자 표시용 에러 메시지"),
			fieldWithPath("data").description("실패 시 null"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
	}

	private org.springframework.restdocs.headers.RequestHeadersSnippet authHeader() {
		return requestHeaders(headerWithName("Authorization").description("Bearer access token"));
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

	private void joinCampus(String accessToken, String inviteCode) throws Exception {
		mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "%s"
					}
					""".formatted(inviteCode)))
			.andExpect(status().isCreated());
	}

	private void createPenaltyPrerequisites(long campusId, long managerId, String accountNumber) {
		createPenaltyRules(campusId);
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

	private void createPenaltyRules(long campusId) {
		penaltyRuleRepository.saveAllAndFlush(List.of(
			PenaltyRule.create(campusId, PenaltyRuleType.QUIET_TIME, PenaltyCalculationType.MISSING_COUNT, 5, 0, 500),
			PenaltyRule.create(campusId, PenaltyRuleType.PRAYER, PenaltyCalculationType.MISSING_COUNT, 5, 0, 500),
			PenaltyRule.create(campusId, PenaltyRuleType.BIBLE_READING, PenaltyCalculationType.MISSING_COUNT, 5, 0, 300),
			PenaltyRule.create(campusId, PenaltyRuleType.SATURDAY_LATE, PenaltyCalculationType.LATE_MINUTE, 0, 1000, 100)
		));
	}

	private String signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "문서경건",
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
}
