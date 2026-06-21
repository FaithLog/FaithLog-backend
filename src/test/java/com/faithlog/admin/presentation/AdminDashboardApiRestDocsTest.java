package com.faithlog.admin.presentation;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.billing.infrastructure.jpa.ChargeItemRepository;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusRole;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.devotion.domain.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.jpa.WeeklyDevotionRecordRepository;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.domain.PollResponse;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
import com.faithlog.poll.infrastructure.jpa.PollResponseRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@ActiveProfiles("test")
class AdminDashboardApiRestDocsTest {

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
	void documents_admin_dashboard_summary_contract() throws Exception {
		String managerToken = signupAndLogin("docs-dashboard-manager@example.com", UserRole.MANAGER, "대시보드관리자");
		String elderToken = signupAndLogin("docs-dashboard-elder@example.com", UserRole.USER, "대시보드장로");
		String memberToken = signupAndLogin("docs-dashboard-member@example.com", UserRole.USER, "대시보드멤버");
		JsonNode campus = createCampus(managerToken, "문서대시보드캠");
		long campusId = campus.path("campusId").asLong();
		String inviteCode = campus.path("inviteCode").asText();
		updateCampusRole(joinCampus(elderToken, inviteCode).path("membershipId").asLong(), CampusRole.ELDER);
		joinCampus(memberToken, inviteCode);
		User manager = userRepository.findByEmail("docs-dashboard-manager@example.com").orElseThrow();
		User elder = userRepository.findByEmail("docs-dashboard-elder@example.com").orElseThrow();
		User member = userRepository.findByEmail("docs-dashboard-member@example.com").orElseThrow();
		LocalDate weekStartDate = LocalDate.of(2026, 6, 8);
		submitWeeklyRecord(campusId, manager.id(), weekStartDate);
		createCharge(campusId, manager.id(), PaymentCategory.PENALTY, ChargeSourceType.DEVOTION_RECORD, 501L, 15000);
		createCharge(campusId, elder.id(), PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE, 502L, 4500);
		Poll openPoll = createOpenPoll(campusId);
		pollResponseRepository.save(PollResponse.create(openPoll.id(), manager.id(), null));
		pollResponseRepository.save(PollResponse.create(openPoll.id(), member.id(), null));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("weekStartDate", weekStartDate.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campus.campusId").value(campusId))
			.andDo(document("admin-dashboard-summary-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("대시보드를 조회할 캠퍼스 ID")),
				queryParameters(parameterWithName("weekStartDate").optional().description("경건생활 요약 기준 주차 월요일. 생략 시 Asia/Seoul 기준 현재 주 월요일")),
				responseFields(apiResponseFields(
					fieldWithPath("data.campus.campusId").description("캠퍼스 ID"),
					fieldWithPath("data.campus.campusName").description("캠퍼스 이름"),
					fieldWithPath("data.campus.region").optional().description("캠퍼스 지역"),
					fieldWithPath("data.members.activeCount").description("ACTIVE 캠퍼스 멤버 수"),
					fieldWithPath("data.members.inactiveCount").description("ACTIVE가 아닌 캠퍼스 멤버 수"),
					fieldWithPath("data.members.adminCount").description("ACTIVE 멤버 중 MINISTER, ELDER, CAMPUS_LEADER 수"),
					fieldWithPath("data.devotion.weekStartDate").description("경건생활 제출 요약 기준 주차 월요일"),
					fieldWithPath("data.devotion.submittedCount").description("기준 주차 제출 인원 수"),
					fieldWithPath("data.devotion.missingCount").description("ACTIVE 멤버 중 기준 주차 미제출 인원 수"),
					fieldWithPath("data.devotion.submitRate").description("ACTIVE 멤버 대비 제출률"),
					fieldWithPath("data.charges.unpaidAmount").description("UNPAID 청구 총액"),
					fieldWithPath("data.charges.unpaidMemberCount").description("UNPAID 청구가 1개 이상 있는 사용자 수"),
					fieldWithPath("data.charges.byCategory[]").description("카테고리별 UNPAID 금액 목록"),
					fieldWithPath("data.charges.byCategory[].paymentCategory").description("청구 카테고리. PENALTY 또는 COFFEE"),
					fieldWithPath("data.charges.byCategory[].unpaidAmount").description("카테고리별 UNPAID 금액"),
					fieldWithPath("data.polls.openCount").description("현재 OPEN 투표 수"),
					fieldWithPath("data.polls.recentlyClosedCount").description("최근 7일 안에 종료된 CLOSED 투표 수"),
					fieldWithPath("data.polls.missingResponseCount").description("OPEN 투표 미응답자 수 총합"),
					fieldWithPath("data.polls.recentlyClosedDays").description("최근 종료 투표 기준 일수")
				))
			));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/dashboard/summary", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("weekStartDate", "2026-06-09"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DEVOTION_INVALID_WEEK_START_DATE"))
			.andDo(document("admin-dashboard-summary-invalid-week-start-date",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("대시보드를 조회할 캠퍼스 ID")),
				queryParameters(parameterWithName("weekStartDate").description("경건생활 요약 기준 주차 월요일")),
				responseFields(errorResponseFields())
			));
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

	private void updateCampusRole(long membershipId, CampusRole campusRole) {
		CampusMember member = campusMemberRepository.findById(membershipId).orElseThrow();
		ReflectionTestUtils.setField(member, "campusRole", campusRole);
		campusMemberRepository.saveAndFlush(member);
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
		int amount
	) {
		chargeItemRepository.saveAndFlush(ChargeItem.create(
			campusId,
			userId,
			paymentCategory,
			1L,
			"문서은행",
			"000-1111",
			"문서",
			sourceType,
			sourceId,
			"문서 청구",
			null,
			amount,
			null
		));
	}

	private Poll createOpenPoll(Long campusId) {
		Poll poll = Poll.create(
			campusId,
			null,
			"문서 투표",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			null
		);
		poll.open();
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

	private static org.springframework.restdocs.headers.RequestHeadersSnippet authHeader() {
		return requestHeaders(
			headerWithName("Authorization").description("`Bearer {accessToken}` 형식의 Access Token")
		);
	}

	private static FieldDescriptor[] apiResponseFields(FieldDescriptor... dataFields) {
		FieldDescriptor[] fields = new FieldDescriptor[5 + dataFields.length];
		fields[0] = fieldWithPath("success").description("요청 성공 여부");
		fields[1] = fieldWithPath("code").description("공통 응답 코드");
		fields[2] = fieldWithPath("message").description("응답 메시지");
		fields[3] = fieldWithPath("data").description("응답 데이터");
		fields[4] = fieldWithPath("timestamp").description("응답 생성 시각");
		System.arraycopy(dataFields, 0, fields, 5, dataFields.length);
		return fields;
	}

	private static FieldDescriptor[] errorResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부"),
			fieldWithPath("code").description("에러 코드"),
			fieldWithPath("message").description("에러 메시지"),
			fieldWithPath("data").description("응답 데이터. 에러 시 null"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
	}
}
