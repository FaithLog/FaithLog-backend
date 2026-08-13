package com.faithlog.shepherd.controller;

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
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
class ShepherdApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Test
	void documents_shepherd_group_and_weekly_attendance_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-shepherd-manager@example.com", UserRole.MANAGER);
		String memberAToken = signupAndLogin("docs-shepherd-a@example.com", UserRole.USER);
		String memberBToken = signupAndLogin("docs-shepherd-b@example.com", UserRole.USER);
		User memberA = userRepository.findByEmail("docs-shepherd-a@example.com").orElseThrow();
		User memberB = userRepository.findByEmail("docs-shepherd-b@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "260목장문서캠");
		long campusId = campus.path("campusId").asLong();
		joinCampus(memberAToken, campus.path("inviteCode").asText());
		joinCampus(memberBToken, campus.path("inviteCode").asText());

		String createdBody = mockMvc.perform(post("/api/v1/campuses/{campusId}/shepherd-groups", campusId)
				.header("Authorization", "Bearer " + memberAToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "믿음  목장",
					  "assigneeUserIds": []
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.assignees[0].userId").value(memberA.id()))
			.andDo(document("shepherd-group-create-member-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				requestFields(
					fieldWithPath("name").description("목장 이름. 서버가 trim 및 공백 정규화 후 저장"),
					fieldWithPath("assigneeUserIds").description("관리자 생성 시 담당자 사용자 ID 목록. 일반 사용자는 무시되고 본인이 자동 담당")
				),
				relaxedResponseFields(groupResponseFields())
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long groupId = objectMapper.readTree(createdBody).path("data").path("groupId").asLong();

		mockMvc.perform(get("/api/v1/campuses/{campusId}/shepherd-groups/me", campusId)
				.header("Authorization", "Bearer " + memberAToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].groupId").value(groupId))
			.andDo(document("shepherd-groups-me-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				relaxedResponseFields(apiResponseFields(
					fieldWithPath("data[]").description("내가 담당자로 연결된 목장 목록"),
					fieldWithPath("data[].groupId").description("목장 ID"),
					fieldWithPath("data[].campusId").description("캠퍼스 ID"),
					fieldWithPath("data[].name").description("목장 이름"),
					fieldWithPath("data[].status").description("목장 상태"),
					fieldWithPath("data[].version").description("목장 수정 version"),
					fieldWithPath("data[].assignees[]").description("목장 담당자 목록"),
					fieldWithPath("data[].assignees[].userId").description("담당자 사용자 ID"),
					fieldWithPath("data[].assignees[].name").description("담당자 이름"),
					fieldWithPath("data[].assignees[].email").description("담당자 이메일")
				))
			));

		mockMvc.perform(put("/api/v1/admin/campuses/{campusId}/shepherd-groups/{groupId}/assignees", campusId, groupId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "assigneeUserIds": [%d, %d]
					}
					""".formatted(memberA.id(), memberB.id())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.assignees[1].userId").value(memberB.id()))
			.andDo(document("shepherd-group-assignees-replace-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("groupId").description("목장 ID")
				),
				requestFields(fieldWithPath("assigneeUserIds[]").description("교체 후 전체 담당자 사용자 ID 목록. 최소 1명")),
				relaxedResponseFields(groupResponseFields())
			));

		mockMvc.perform(put("/api/v1/campuses/{campusId}/shepherd-groups/{groupId}/attendance/{serviceDate}",
				campusId, groupId, "2026-08-16")
				.header("Authorization", "Bearer " + memberAToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "smallGroupMeetingCount": 7,
					  "holyWaveCount": 4,
					  "otherWorshipCount": 2,
					  "note": "중복 집계 가능",
					  "status": "SUBMITTED",
					  "version": 0
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.version").value(1))
			.andDo(document("shepherd-attendance-save-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("groupId").description("목장 ID"),
					parameterWithName("serviceDate").description("집계 기준 일요일 날짜")
				),
				requestFields(attendanceRequestFields()),
				relaxedResponseFields(attendanceResponseFields())
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/shepherd-groups/{groupId}/attendance/{serviceDate}",
				campusId, groupId, "2026-08-16")
				.header("Authorization", "Bearer " + memberAToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.smallGroupMeetingCount").value(7))
			.andDo(document("shepherd-attendance-get-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("groupId").description("목장 ID"),
					parameterWithName("serviceDate").description("집계 기준 일요일 날짜")
				),
				relaxedResponseFields(attendanceResponseFields())
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/shepherd-attendance/me/home", campusId)
				.header("Authorization", "Bearer " + memberAToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.visible").value(true))
			.andExpect(jsonPath("$.data.title").value("이번 주 목홀타를 입력해 주세요"))
			.andExpect(jsonPath("$.data.assignedGroupCount").value(2))
			.andExpect(jsonPath("$.data.submittedGroupCount").value(1))
			.andExpect(jsonPath("$.data.groups[0].report.status").value("SUBMITTED"))
			.andDo(document("shepherd-attendance-home-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				relaxedResponseFields(homeResponseFields())
			));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/shepherd-attendance", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("serviceDate", "2026-08-16")
				.param("page", "0")
				.param("size", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalSmallGroupMeetingCount").value(7))
			.andDo(document("shepherd-attendance-admin-board-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				queryParameters(
					parameterWithName("serviceDate").description("집계 기준 일요일 날짜"),
					parameterWithName("page").description("0부터 시작하는 페이지 번호"),
					parameterWithName("size").description("페이지 크기. 최대 100")
				),
				relaxedResponseFields(boardResponseFields())
			));

		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/shepherd-groups/{groupId}", campusId, groupId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "믿음 목장 수정",
					  "version": 1
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("믿음 목장 수정"))
			.andDo(document("shepherd-group-update-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("groupId").description("목장 ID")
				),
				requestFields(
					fieldWithPath("name").description("수정할 목장 이름"),
					fieldWithPath("version").description("조회 응답에서 받은 목장 version")
				),
				relaxedResponseFields(groupResponseFields())
			));

		mockMvc.perform(put("/api/v1/campuses/{campusId}/shepherd-groups/{groupId}/attendance/{serviceDate}",
				campusId, groupId, "2026-08-17")
				.header("Authorization", "Bearer " + memberAToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "smallGroupMeetingCount": 1,
					  "holyWaveCount": 1,
					  "otherWorshipCount": 1,
					  "status": "DRAFT",
					  "version": 0
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SHEPHERD_INVALID_SERVICE_DATE"))
			.andDo(document("shepherd-attendance-save-non-sunday",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("groupId").description("목장 ID"),
					parameterWithName("serviceDate").description("일요일이어야 하는 집계 기준 날짜")
				),
				requestFields(attendanceRequestFields()),
				responseFields(errorResponseFields())
			));
	}

	private FieldDescriptor[] groupResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.groupId").description("목장 ID"),
			fieldWithPath("data.campusId").description("캠퍼스 ID"),
			fieldWithPath("data.name").description("목장 이름"),
			fieldWithPath("data.status").description("목장 상태"),
			fieldWithPath("data.version").description("목장 수정 version"),
			fieldWithPath("data.assignees[]").description("목장 담당자 목록"),
			fieldWithPath("data.assignees[].userId").description("담당자 사용자 ID"),
			fieldWithPath("data.assignees[].name").description("담당자 이름"),
			fieldWithPath("data.assignees[].email").description("담당자 이메일")
		);
	}

	private FieldDescriptor[] attendanceRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("smallGroupMeetingCount").description("목장모임 참여 인원. 0 이상"),
			fieldWithPath("holyWaveCount").description("홀리웨이브 참여 인원. 0 이상"),
			fieldWithPath("otherWorshipCount").description("타예배 참여 인원. 0 이상"),
			fieldWithPath("note").type(JsonFieldType.STRING).optional().description("선택 메모. 최대 500자"),
			fieldWithPath("status").description("저장 상태. DRAFT 또는 SUBMITTED"),
			fieldWithPath("version").description("stale-write 방지 version. 신규 저장은 0")
		};
	}

	private FieldDescriptor[] attendanceResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.reportId").description("목홀타 보고서 ID"),
			fieldWithPath("data.campusId").type(JsonFieldType.NUMBER).optional().description("캠퍼스 ID. 관리자 board row 내부에서는 null일 수 있음"),
			fieldWithPath("data.groupId").description("목장 ID"),
			fieldWithPath("data.serviceDate").description("집계 기준 일요일 날짜"),
			fieldWithPath("data.smallGroupMeetingCount").description("목장모임 참여 인원"),
			fieldWithPath("data.holyWaveCount").description("홀리웨이브 참여 인원"),
			fieldWithPath("data.otherWorshipCount").description("타예배 참여 인원"),
			fieldWithPath("data.note").type(JsonFieldType.STRING).optional().description("선택 메모"),
			fieldWithPath("data.status").description("DRAFT 또는 SUBMITTED"),
			fieldWithPath("data.lastModifiedByUserId").description("마지막 수정자 사용자 ID"),
			fieldWithPath("data.lastModifiedByName").type(JsonFieldType.STRING).optional().description("마지막 수정자 이름"),
			fieldWithPath("data.lastModifiedAt").description("마지막 수정 시각"),
			fieldWithPath("data.version").description("보고서 수정 version")
		);
	}

	private FieldDescriptor[] boardResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.campusId").description("캠퍼스 ID"),
			fieldWithPath("data.serviceDate").description("집계 기준 일요일 날짜"),
			fieldWithPath("data.page").description("현재 페이지 번호"),
			fieldWithPath("data.size").description("페이지 크기"),
			fieldWithPath("data.totalElements").description("전체 활성 목장 수"),
			fieldWithPath("data.totalPages").description("전체 페이지 수"),
			fieldWithPath("data.totalSubmittedCount").description("보고서가 있는 목장 수"),
			fieldWithPath("data.totalMissingCount").description("보고서가 없는 목장 수"),
			fieldWithPath("data.totalSmallGroupMeetingCount").description("캠퍼스 목장모임 합계"),
			fieldWithPath("data.totalHolyWaveCount").description("캠퍼스 홀리웨이브 합계"),
			fieldWithPath("data.totalOtherWorshipCount").description("캠퍼스 타예배 합계"),
			fieldWithPath("data.groups[]").description("목장별 주차 현황"),
			fieldWithPath("data.groups[].groupId").description("목장 ID"),
			fieldWithPath("data.groups[].groupName").description("목장 이름"),
			fieldWithPath("data.groups[].groupVersion").description("목장 version"),
			fieldWithPath("data.groups[].assignees[]").description("담당자 목록"),
			fieldWithPath("data.groups[].assignees[].userId").description("담당자 사용자 ID"),
			fieldWithPath("data.groups[].assignees[].name").description("담당자 이름"),
			fieldWithPath("data.groups[].assignees[].email").description("담당자 이메일"),
			fieldWithPath("data.groups[].report").type(JsonFieldType.OBJECT).optional().description("해당 일요일 보고서. 미제출은 null")
		);
	}

	private FieldDescriptor[] homeResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.visible").description("Asia/Seoul 기준 현재 시각이 일요일이고 ACTIVE campus 담당 목장이 있으면 true"),
			fieldWithPath("data.title").type(JsonFieldType.STRING).optional().description("노출 카드 문구. 노출 시 '이번 주 목홀타를 입력해 주세요'"),
			fieldWithPath("data.serviceDate").type(JsonFieldType.STRING).optional().description("Asia/Seoul 기준 현재 일요일 날짜. 비일요일 또는 미노출 시 null"),
			fieldWithPath("data.assignedGroupCount").description("현재 사용자가 담당자인 활성 목장 수. 미노출 시 0"),
			fieldWithPath("data.submittedGroupCount").description("현재 일요일 보고서가 SUBMITTED인 담당 목장 수. 미노출 시 0"),
			fieldWithPath("data.groups[]").description("담당 목장별 현재 일요일 입력 상태. 비일요일 또는 미노출 시 빈 배열"),
			fieldWithPath("data.groups[].groupId").description("목장 ID"),
			fieldWithPath("data.groups[].groupName").description("목장 이름"),
			fieldWithPath("data.groups[].report").type(JsonFieldType.OBJECT).optional().description("현재 일요일 보고서. 신규 입력 대상은 null"),
			fieldWithPath("data.groups[].report.reportId").type(JsonFieldType.NUMBER).optional().description("목홀타 보고서 ID"),
			fieldWithPath("data.groups[].report.smallGroupMeetingCount").type(JsonFieldType.NUMBER).optional().description("목장모임 참여 인원"),
			fieldWithPath("data.groups[].report.holyWaveCount").type(JsonFieldType.NUMBER).optional().description("홀리웨이브 참여 인원"),
			fieldWithPath("data.groups[].report.otherWorshipCount").type(JsonFieldType.NUMBER).optional().description("타예배 참여 인원"),
			fieldWithPath("data.groups[].report.note").type(JsonFieldType.STRING).optional().description("선택 메모"),
			fieldWithPath("data.groups[].report.status").type(JsonFieldType.STRING).optional().description("DRAFT 또는 SUBMITTED"),
			fieldWithPath("data.groups[].report.version").type(JsonFieldType.NUMBER).optional().description("보고서 수정 version"),
			fieldWithPath("data.groups[].report.lastModifiedAt").type(JsonFieldType.STRING).optional().description("마지막 수정 시각")
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
					  "name": "목장문서",
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

	@TestConfiguration
	static class ShepherdDocsClockConfig {

		@Bean
		@Primary
		Clock shepherdDocsClock() {
			return Clock.fixed(Instant.parse("2026-08-16T03:00:00Z"), ZoneOffset.UTC);
		}
	}
}
