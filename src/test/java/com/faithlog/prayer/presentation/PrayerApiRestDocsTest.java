package com.faithlog.prayer.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.prayer.infrastructure.jpa.PrayerSubmissionRepository;
import com.faithlog.prayer.infrastructure.jpa.PrayerWeekRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.headers.RequestHeadersSnippet;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@ActiveProfiles("test")
class PrayerApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PrayerWeekRepository prayerWeekRepository;

	@Autowired
	private PrayerSubmissionRepository prayerSubmissionRepository;

	@Test
	void documents_prayer_season_group_board_and_submission_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-prayer-manager@example.com", UserRole.MANAGER);
		String memberAToken = signupAndLogin("docs-prayer-a@example.com", UserRole.USER);
		String memberBToken = signupAndLogin("docs-prayer-b@example.com", UserRole.USER);
		String memberCToken = signupAndLogin("docs-prayer-c@example.com", UserRole.USER);
		User memberA = userRepository.findByEmail("docs-prayer-a@example.com").orElseThrow();
		User memberB = userRepository.findByEmail("docs-prayer-b@example.com").orElseThrow();
		User memberC = userRepository.findByEmail("docs-prayer-c@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "45문서캠");
		long campusId = campus.path("campusId").asLong();
		joinCampus(memberAToken, campus.path("inviteCode").asText());
		joinCampus(memberBToken, campus.path("inviteCode").asText());
		joinCampus(memberCToken, campus.path("inviteCode").asText());

		String seasonBody = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/prayer-seasons", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "2026 여름 나눔조",
					  "startDate": "2026-06-01"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("ACTIVE"))
			.andDo(document("prayer-season-create-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				requestFields(
					fieldWithPath("name").description("기도 시즌 이름"),
					fieldWithPath("startDate").description("기도 시즌 시작일")
				),
				relaxedResponseFields(apiResponseFields(
					fieldWithPath("data.seasonId").description("기도 시즌 ID"),
					fieldWithPath("data.campusId").description("캠퍼스 ID"),
					fieldWithPath("data.name").description("기도 시즌 이름"),
					fieldWithPath("data.startDate").description("기도 시즌 시작일"),
					fieldWithPath("data.endDate").type(JsonFieldType.NULL).description("기도 시즌 종료일. 활성 시즌은 null 가능"),
					fieldWithPath("data.status").description("기도 시즌 상태")
				))
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long seasonId = objectMapper.readTree(seasonBody).path("data").path("seasonId").asLong();

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/prayer-seasons/current", campusId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.seasonId").value(seasonId))
			.andExpect(jsonPath("$.data.endDate").isEmpty())
			.andDo(document("prayer-season-current-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				relaxedResponseFields(apiResponseFields(
					fieldWithPath("data.seasonId").description("현재 운영 중인 기도 시즌 ID"),
					fieldWithPath("data.campusId").description("캠퍼스 ID"),
					fieldWithPath("data.name").description("기도 시즌 이름"),
					fieldWithPath("data.startDate").description("기도 시즌 시작일"),
					fieldWithPath("data.endDate").type(JsonFieldType.NULL).description("현재 운영 중인 시즌은 null"),
					fieldWithPath("data.status").description("기도 시즌 상태. 현재 운영 기간은 ACTIVE")
				))
			));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/prayer-seasons/current", campusId)
				.header("Authorization", "Bearer " + memberAToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PRAYER_MANAGE_FORBIDDEN"))
			.andDo(document("prayer-season-current-member-forbidden",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				responseFields(errorResponseFields())
			));

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/prayer-seasons", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "중복 활성 시즌",
					  "startDate": "2026-07-01"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRAYER_ACTIVE_SEASON_ALREADY_EXISTS"))
			.andDo(document("prayer-season-create-active-duplicate",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				responseFields(errorResponseFields())
			));

		String groupABody = createPrayerGroup(managerToken, seasonId, "1조", 1);
		long groupAId = objectMapper.readTree(groupABody).path("data").path("groupId").asLong();
		String groupBBody = createPrayerGroup(managerToken, seasonId, "2조", 2);
		long groupBId = objectMapper.readTree(groupBBody).path("data").path("groupId").asLong();

		mockMvc.perform(patch("/api/v1/admin/prayer-groups/{groupId}", groupBId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "2조 수정",
					  "sortOrder": 3,
					  "isActive": true
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("2조 수정"))
			.andDo(document("prayer-group-update-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("groupId").description("기도조 ID")),
				requestFields(
					fieldWithPath("name").description("수정할 기도조 이름. 선택 입력"),
					fieldWithPath("sortOrder").description("수정할 정렬 순서. 선택 입력"),
					fieldWithPath("isActive").description("활성 여부. 선택 입력")
				),
				relaxedResponseFields(groupResponseFields())
			));

		replaceGroupMembers(managerToken, groupAId, memberA.id(), memberB.id());
		replaceGroupMembers(managerToken, groupBId, memberC.id());

		mockMvc.perform(put("/api/v1/admin/prayer-groups/{groupId}/members", groupBId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userIds": [%d, %d]
					}
					""".formatted(memberA.id(), memberC.id())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRAYER_GROUP_MEMBER_ALREADY_ASSIGNED"))
			.andDo(document("prayer-group-members-duplicate-assignment-conflict",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("groupId").description("기도조 ID")),
				requestFields(fieldWithPath("userIds[]").description("전체 교체할 사용자 ID 목록")),
				responseFields(errorResponseFields())
			));

		mockMvc.perform(get("/api/v1/admin/prayer-seasons/{seasonId}/groups", seasonId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].groupId").value(groupAId))
			.andExpect(jsonPath("$.data[0].members[0].email").value(memberA.email()))
			.andDo(document("prayer-season-groups-get-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("seasonId").description("기도 시즌 ID")),
				relaxedResponseFields(apiResponseFields(
					fieldWithPath("data[]").description("기도 시즌의 활성 기도조 목록"),
					fieldWithPath("data[].groupId").description("기도조 ID"),
					fieldWithPath("data[].seasonId").description("기도 시즌 ID"),
					fieldWithPath("data[].name").description("기도조 이름"),
					fieldWithPath("data[].sortOrder").description("정렬 순서"),
					fieldWithPath("data[].active").description("활성 여부"),
					fieldWithPath("data[].members[]").description("활성 조원 목록"),
					fieldWithPath("data[].members[].userId").description("조원 사용자 ID"),
					fieldWithPath("data[].members[].name").description("조원 이름"),
					fieldWithPath("data[].members[].email").description("조원 이메일")
				))
			));

		mockMvc.perform(get("/api/v1/admin/prayer-seasons/{seasonId}/members/assignable", seasonId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[1].assignedGroupId").value(groupAId))
			.andExpect(jsonPath("$.data[1].assignable").value(false))
			.andDo(document("prayer-season-assignable-members-get-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("seasonId").description("기도 시즌 ID")),
				relaxedResponseFields(apiResponseFields(
					fieldWithPath("data[]").description("기도조 배정 가능 여부를 포함한 캠퍼스 ACTIVE 멤버 목록"),
					fieldWithPath("data[].userId").description("사용자 ID"),
					fieldWithPath("data[].name").description("사용자 이름"),
					fieldWithPath("data[].email").description("사용자 이메일"),
					fieldWithPath("data[].assignedGroupId").type(JsonFieldType.VARIES).optional().description("이미 배정된 active 기도조 ID. 미배정은 null"),
					fieldWithPath("data[].assignedGroupName").type(JsonFieldType.VARIES).optional().description("이미 배정된 active 기도조 이름. 미배정은 null"),
					fieldWithPath("data[].assignable").description("현재 season에서 active 기도조에 미배정이면 true")
				))
			));

		long weekCountBeforeGet = prayerWeekRepository.count();
		long submissionCountBeforeGet = prayerSubmissionRepository.count();
		mockMvc.perform(get("/api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}", campusId, "2026-06-22")
				.header("Authorization", "Bearer " + memberAToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.currentSeason.seasonId").value(seasonId))
			.andExpect(jsonPath("$.data.myGroupId").value(groupAId))
			.andExpect(jsonPath("$.data.submittedCount").value(0))
			.andExpect(jsonPath("$.data.targetMemberCount").value(3))
			.andExpect(jsonPath("$.data.groups[0].seasonId").value(seasonId))
			.andExpect(jsonPath("$.data.groups[0].members[0].submissionId").isEmpty())
			.andExpect(jsonPath("$.data.groups[0].members[0].content").isEmpty())
			.andExpect(jsonPath("$.data.groups[0].members[0].submitted").value(false))
			.andExpect(jsonPath("$.data.groups[0].members[0].editable").value(true))
			.andExpect(jsonPath("$.data.groups[0].members[1].editable").value(false))
			.andExpect(jsonPath("$.data.groups[0].members[0].version").value(0))
			.andDo(document("prayer-week-board-get-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("조회 주차의 월요일")
				),
				relaxedResponseFields(boardResponseFields())
			));
		assertThat(prayerWeekRepository.count()).isEqualTo(weekCountBeforeGet);
		assertThat(prayerSubmissionRepository.count()).isEqualTo(submissionCountBeforeGet);

		mockMvc.perform(put("/api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}/submissions", campusId, "2026-06-29")
				.header("Authorization", "Bearer " + memberAToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "submissions": [
					    {"userId": %d, "content": "기도제목 A", "version": 0},
					    {"userId": %d, "content": null, "version": 0}
					  ]
					}
					""".formatted(memberA.id(), memberB.id())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.weekStartDate").value("2026-06-29"))
			.andExpect(jsonPath("$.data.submittedCount").value(2))
			.andExpect(jsonPath("$.data.groups[0].members[0].version").value(1))
			.andDo(document("prayer-submissions-save-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("저장할 주차의 월요일")
				),
				requestFields(
					fieldWithPath("submissions[]").description("사람별 기도제목 저장 요청 목록"),
					fieldWithPath("submissions[].userId").description("저장 대상 사용자 ID"),
					fieldWithPath("submissions[].content").type(JsonFieldType.STRING).optional().description("기도제목 내용. null 저장 가능"),
					fieldWithPath("submissions[].version").description("클라이언트가 조회한 현재 version")
				),
				relaxedResponseFields(boardResponseFields())
			));

		mockMvc.perform(put("/api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}/submissions", campusId, "2026-06-29")
				.header("Authorization", "Bearer " + memberAToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "submissions": [
					    {"userId": %d, "content": "다른 조 저장", "version": 0}
					  ]
					}
					""".formatted(memberC.id())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PRAYER_SUBMISSION_FORBIDDEN"))
			.andDo(document("prayer-submissions-save-other-group-forbidden",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("저장할 주차의 월요일")
				),
				responseFields(errorResponseFields())
			));

		mockMvc.perform(put("/api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}/submissions", campusId, "2026-06-29")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "submissions": [
					    {"userId": %d, "content": "충돌", "version": 0},
					    {"userId": %d, "content": "롤백되어야 함", "version": 0}
					  ]
					}
					""".formatted(memberA.id(), memberC.id())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRAYER_SUBMISSION_CONFLICT"))
			.andDo(document("prayer-submissions-save-version-conflict",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("저장할 주차의 월요일")
				),
				responseFields(errorResponseFields())
			));

		mockMvc.perform(put("/api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}/me", campusId, "2026-07-06")
				.header("Authorization", "Bearer " + memberAToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "content": "이번 주 제 기도제목입니다."
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.weekStartDate").value("2026-07-06"))
			.andExpect(jsonPath("$.data.groups[0].members[0].content").value("이번 주 제 기도제목입니다."))
			.andExpect(jsonPath("$.data.groups[0].members[0].version").value(1))
			.andDo(document("prayer-my-submission-save-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("저장할 주차의 월요일")
				),
				requestFields(fieldWithPath("content").type(JsonFieldType.STRING).optional().description("본인 기도제목 내용. null 저장 가능")),
				relaxedResponseFields(boardResponseFields())
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}", campusId, "2026-06-23")
				.header("Authorization", "Bearer " + memberAToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PRAYER_INVALID_WEEK_START_DATE"))
			.andDo(document("prayer-week-board-invalid-week-start",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("weekStartDate").description("조회 주차의 월요일")
				),
				responseFields(errorResponseFields())
			));

		mockMvc.perform(patch("/api/v1/admin/prayer-seasons/{seasonId}/close", seasonId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "endDate": "2026-09-01"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CLOSED"))
			.andDo(document("prayer-season-close-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("seasonId").description("기도 시즌 ID")),
				requestFields(fieldWithPath("endDate").description("기도 시즌 종료일")),
				relaxedResponseFields(apiResponseFields(
					fieldWithPath("data.seasonId").description("기도 시즌 ID"),
					fieldWithPath("data.campusId").description("캠퍼스 ID"),
					fieldWithPath("data.name").description("기도 시즌 이름"),
					fieldWithPath("data.startDate").description("기도 시즌 시작일"),
					fieldWithPath("data.endDate").description("기도 시즌 종료일"),
					fieldWithPath("data.status").description("기도 시즌 상태")
				))
			));
	}

	private String createPrayerGroup(String accessToken, long seasonId, String name, int sortOrder) throws Exception {
		return mockMvc.perform(post("/api/v1/admin/prayer-seasons/{seasonId}/groups", seasonId)
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "sortOrder": %d
					}
					""".formatted(name, sortOrder)))
			.andExpect(status().isCreated())
			.andDo(document("prayer-group-create-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("seasonId").description("기도 시즌 ID")),
				requestFields(
					fieldWithPath("name").description("기도조 이름"),
					fieldWithPath("sortOrder").description("정렬 순서")
				),
				relaxedResponseFields(groupResponseFields())
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private void replaceGroupMembers(String accessToken, long groupId, Long... userIds) throws Exception {
		String ids = java.util.Arrays.stream(userIds)
			.map(String::valueOf)
			.collect(java.util.stream.Collectors.joining(", "));
		mockMvc.perform(put("/api/v1/admin/prayer-groups/{groupId}/members", groupId)
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userIds": [%s]
					}
					""".formatted(ids)))
			.andExpect(status().isOk())
			.andDo(document("prayer-group-members-replace-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("groupId").description("기도조 ID")),
				requestFields(fieldWithPath("userIds[]").description("전체 교체할 사용자 ID 목록")),
				relaxedResponseFields(groupResponseFields())
			));
	}

	private FieldDescriptor[] groupResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.groupId").description("기도조 ID"),
			fieldWithPath("data.seasonId").description("기도 시즌 ID"),
			fieldWithPath("data.name").description("기도조 이름"),
			fieldWithPath("data.sortOrder").description("정렬 순서"),
			fieldWithPath("data.active").description("활성 여부"),
			fieldWithPath("data.members[]").description("활성 조원 목록"),
			fieldWithPath("data.members[].userId").type(JsonFieldType.NUMBER).optional().description("조원 사용자 ID"),
			fieldWithPath("data.members[].name").type(JsonFieldType.STRING).optional().description("조원 이름"),
			fieldWithPath("data.members[].email").type(JsonFieldType.STRING).optional().description("조원 이메일")
		);
	}

	private FieldDescriptor[] boardResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.campusId").description("캠퍼스 ID"),
			fieldWithPath("data.weekStartDate").description("조회/저장 주차 월요일"),
			fieldWithPath("data.weekEndDate").description("조회/저장 주차 일요일"),
			fieldWithPath("data.currentSeason").description("현재 운영 중인 기도 시즌. 없으면 null"),
			fieldWithPath("data.currentSeason.seasonId").description("현재 기도 시즌 ID"),
			fieldWithPath("data.currentSeason.campusId").description("현재 기도 시즌 캠퍼스 ID"),
			fieldWithPath("data.currentSeason.name").description("현재 기도 시즌 이름"),
			fieldWithPath("data.currentSeason.startDate").description("현재 기도 시즌 시작일"),
			fieldWithPath("data.currentSeason.endDate").type(JsonFieldType.NULL).description("현재 운영 중인 시즌은 null"),
			fieldWithPath("data.currentSeason.status").description("현재 기도 시즌 상태"),
			fieldWithPath("data.myGroupId").type(JsonFieldType.NUMBER).optional().description("로그인 사용자가 배정된 현재 시즌 active 기도조 ID. 미배정은 null"),
			fieldWithPath("data.status").description("주차 게시판 상태"),
			fieldWithPath("data.submittedCount").description("작성 완료 인원 수"),
			fieldWithPath("data.targetMemberCount").description("작성 대상 활성 조원 수"),
			fieldWithPath("data.groups[]").description("활성 기도조 목록"),
			fieldWithPath("data.groups[].groupId").description("기도조 ID"),
			fieldWithPath("data.groups[].seasonId").description("기도 시즌 ID"),
			fieldWithPath("data.groups[].groupName").description("기도조 이름"),
			fieldWithPath("data.groups[].sortOrder").description("기도조 정렬 순서"),
			fieldWithPath("data.groups[].members[]").description("기도조 활성 조원별 기도제목"),
			fieldWithPath("data.groups[].members[].userId").description("조원 사용자 ID"),
			fieldWithPath("data.groups[].members[].name").description("조원 이름"),
			fieldWithPath("data.groups[].members[].submissionId").type(JsonFieldType.NUMBER).optional().description("기도제목 row ID. 미작성은 null"),
			fieldWithPath("data.groups[].members[].content").type(JsonFieldType.STRING).optional().description("기도제목 내용. 미작성 또는 null 저장 시 null 가능"),
			fieldWithPath("data.groups[].members[].submitted").description("저장된 기도제목 row가 있으면 true"),
			fieldWithPath("data.groups[].members[].editable").description("로그인 사용자가 해당 항목을 수정할 수 있으면 true"),
			fieldWithPath("data.groups[].members[].version").description("기도제목 version. 미작성은 0"),
			fieldWithPath("data.groups[].members[].submittedAt").type(JsonFieldType.STRING).optional().description("실제 저장 시각. 미작성은 null")
		);
	}

	private FieldDescriptor[] errorResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 오류 응답에서는 false"),
			fieldWithPath("code").description("HTTP status와 함께 고정 계약으로 사용하는 세부 오류 코드"),
			fieldWithPath("message").description("사용자 표시용 오류 메시지"),
			fieldWithPath("data").type(JsonFieldType.NULL).description("오류 응답에서는 null"),
			fieldWithPath("timestamp").description("응답 시각")
		};
	}

	private FieldDescriptor[] apiResponseFields(FieldDescriptor... dataFields) {
		FieldDescriptor[] common = new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부"),
			fieldWithPath("code").description("응답 코드"),
			fieldWithPath("message").description("응답 메시지"),
			fieldWithPath("timestamp").description("응답 시각")
		};
		FieldDescriptor[] combined = new FieldDescriptor[common.length + dataFields.length];
		System.arraycopy(common, 0, combined, 0, common.length);
		System.arraycopy(dataFields, 0, combined, common.length, dataFields.length);
		return combined;
	}

	private RequestHeadersSnippet authHeader() {
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

	private String signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "기도문서",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isCreated());

		User user = userRepository.findByEmail(email).orElseThrow();
		ReflectionTestUtils.setField(user, "role", role);
		userRepository.saveAndFlush(user);

		String body = mockMvc.perform(post("/api/v1/auth/login")
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
		return objectMapper.readTree(body).path("data").path("accessToken").asText();
	}
}
