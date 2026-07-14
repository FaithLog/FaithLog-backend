package com.faithlog.admin.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
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
class AdminManagementApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Test
	void documents_service_admin_user_and_campus_management_contracts() throws Exception {
		String adminToken = signupAndLogin("docs-admin-management-admin@example.com", UserRole.ADMIN, "서비스관리자");
		String managerToken = signupAndLogin("docs-admin-management-manager@example.com", UserRole.MANAGER, "서비스매니저");
		String memberToken = signupAndLogin("docs-admin-management-member@example.com", UserRole.USER, "문서회원");
		User manager = userRepository.findByEmail("docs-admin-management-manager@example.com").orElseThrow();
		User member = userRepository.findByEmail("docs-admin-management-member@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "관리문서캠");
		long campusId = campus.path("campusId").asLong();
		JsonNode membership = joinCampus(memberToken, campus.path("inviteCode").asText());
		updateCampusRole(membership.path("membershipId").asLong(), CampusRole.ELDER);

		mockMvc.perform(get("/api/v1/admin/users")
				.header("Authorization", "Bearer " + adminToken)
				.param("name", "서비스")
				.param("email", "manager")
				.param("userId", manager.id().toString())
				.param("role", "MANAGER")
				.param("page", "0")
				.param("size", "20")
				.param("sort", "email,asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[0].userId").value(manager.id()))
			.andDo(document("admin-users-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				queryParameters(
					parameterWithName("name").optional().description("사용자 이름 검색어"),
					parameterWithName("email").optional().description("사용자 이메일 검색어"),
					parameterWithName("userId").optional().description("사용자 ID exact 필터"),
					parameterWithName("role").optional().description("전역 역할 필터. `USER`, `MANAGER`, `ADMIN`"),
					parameterWithName("page").optional().description("페이지 번호. 기본 0"),
					parameterWithName("size").optional().description("페이지 크기. 기본 20, 최대 100"),
					parameterWithName("sort").optional().description("정렬. 허용 필드: `id`, `name`, `email`, `role`, `createdAt`")
				),
				responseFields(apiResponseFields(pageFields(
					fieldWithPath("data.content[].userId").description("사용자 ID"),
					fieldWithPath("data.content[].name").description("사용자 이름"),
					fieldWithPath("data.content[].email").description("사용자 이메일"),
					fieldWithPath("data.content[].role").description("전역 역할"),
					fieldWithPath("data.content[].campusCount").description("소속 캠퍼스 수"),
					fieldWithPath("data.content[].campuses[]").description("소속 캠퍼스 요약 목록"),
					fieldWithPath("data.content[].campuses[].membershipId").description("캠퍼스 멤버십 ID"),
					fieldWithPath("data.content[].campuses[].campusId").description("캠퍼스 ID"),
					fieldWithPath("data.content[].campuses[].campusName").description("캠퍼스 이름"),
					fieldWithPath("data.content[].campuses[].region").optional().description("캠퍼스 지역"),
					fieldWithPath("data.content[].campuses[].campusRole").description("캠퍼스 내부 역할"),
					fieldWithPath("data.content[].campuses[].status").description("캠퍼스 멤버십 상태")
				)))
			));

		mockMvc.perform(get("/api/v1/admin/users/{userId}", member.id())
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campuses[0].status").value("ACTIVE"))
			.andDo(document("admin-user-detail-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("userId").description("조회할 사용자 ID")),
				responseFields(apiResponseFields(userDetailFields("data.")))
			));

		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", member.id())
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "role": "MANAGER"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.role").value("MANAGER"))
			.andDo(document("admin-user-role-change-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("userId").description("역할을 변경할 사용자 ID")),
				requestFields(fieldWithPath("role").description("새 전역 역할. `USER`, `MANAGER`, `ADMIN`")),
				responseFields(apiResponseFields(userDetailFields("data.")))
			));

		mockMvc.perform(patch("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "관리문서캠",
					  "region": "분당",
					  "description": "관리자 문서용 캠퍼스",
					  "isActive": false
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.isActive").value(false))
			.andDo(document("campus-update-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("수정할 캠퍼스 ID")),
				requestFields(
					fieldWithPath("name").optional().description("수정할 캠퍼스 이름"),
					fieldWithPath("region").optional().description("수정할 캠퍼스 지역"),
					fieldWithPath("description").optional().description("수정할 캠퍼스 설명"),
					fieldWithPath("isActive").optional().description("캠퍼스 운영 여부. `false`이면 관리자 목록 status는 `PAUSED`")
				),
				responseFields(apiResponseFields(campusDetailFields("data.")))
			));

		mockMvc.perform(get("/api/v1/admin/campuses")
				.header("Authorization", "Bearer " + adminToken)
				.param("name", "관리")
				.param("region", "분당")
				.param("status", "PAUSED")
				.param("page", "0")
				.param("size", "20")
				.param("sort", "name,asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[0].status").value("PAUSED"))
			.andDo(document("admin-campuses-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				queryParameters(
					parameterWithName("name").optional().description("캠퍼스 이름 검색어"),
					parameterWithName("region").optional().description("캠퍼스 지역 검색어"),
					parameterWithName("status").optional().description("운영 상태 필터. `ACTIVE`, `PAUSED`"),
					parameterWithName("page").optional().description("페이지 번호. 기본 0"),
					parameterWithName("size").optional().description("페이지 크기. 기본 20, 최대 100"),
					parameterWithName("sort").optional().description("정렬. 허용 필드: `id`, `name`, `region`, `createdAt`")
				),
				responseFields(apiResponseFields(pageFields(
					fieldWithPath("data.content[].campusId").description("캠퍼스 ID"),
					fieldWithPath("data.content[].name").description("캠퍼스 이름"),
					fieldWithPath("data.content[].region").optional().description("캠퍼스 지역"),
					fieldWithPath("data.content[].isActive").description("캠퍼스 활성 여부"),
					fieldWithPath("data.content[].status").description("운영 상태. `ACTIVE` 또는 `PAUSED`"),
					fieldWithPath("data.content[].memberCount").description("ACTIVE 캠퍼스 멤버 수"),
					fieldWithPath("data.content[].adminCount").description("ACTIVE 캠퍼스 관리자 역할 멤버 수")
				)))
			));

		User directMember = userRepository.saveAndFlush(User.create("직접추가", "docs-admin-direct@example.com", "dummy-password"));
		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/members", campusId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userId": %d
					}
					""".formatted(directMember.id())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.campusRole").value("MEMBER"))
			.andDo(document("admin-campus-member-add-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("멤버를 추가할 캠퍼스 ID")),
				requestFields(fieldWithPath("userId").description("추가할 사용자 ID")),
				responseFields(apiResponseFields(campusMemberFields("data.")))
			));
	}

	@Test
	void documents_stale_duty_assignment_recovery_list() throws Exception {
		String managerToken = signupAndLogin(
			"docs-stale-duty-recovery-manager@example.com", UserRole.MANAGER, "복구관리자"
		);
		String memberToken = signupAndLogin(
			"docs-stale-duty-recovery-member@example.com", UserRole.USER, "과거담당자"
		);
		User member = userRepository.findByEmail("docs-stale-duty-recovery-member@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "과거담당복구캠");
		long campusId = campus.path("campusId").asLong();
		JsonNode membership = joinCampus(memberToken, campus.path("inviteCode").asText());
		String assignmentBody = mockMvc.perform(put(
				"/api/v1/admin/campuses/{campusId}/duty-assignments/coffee", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userId": %d
					}
					""".formatted(member.id())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long assignmentId = objectMapper.readTree(assignmentBody).path("data").path("assignmentId").asLong();
		CampusMember inactiveMember = campusMemberRepository
			.findById(membership.path("membershipId").asLong())
			.orElseThrow();
		inactiveMember.deactivate();
		campusMemberRepository.saveAndFlush(inactiveMember);

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/duty-assignments", campusId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/duty-assignments", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("staleOnly", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].assignmentId").value(assignmentId))
			.andExpect(jsonPath("$.data[0].userId").value(member.id()))
			.andDo(document("admin-stale-duty-assignments-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("과거 담당 배정을 정리할 캠퍼스 ID")),
				queryParameters(parameterWithName("staleOnly").description(
					"`true`이면 INACTIVE 멤버십에 남은 ACTIVE 담당 배정만 반환"
				)),
				responseFields(apiResponseFields(dutyAssignmentFields("data[].")))
			));

		mockMvc.perform(delete(
				"/api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}",
				campusId,
				assignmentId
			).header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "%s"
					}
					""".formatted(campus.path("inviteCode").asText())))
			.andExpect(status().isCreated());
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

	private static FieldDescriptor[] pageFields(FieldDescriptor... contentFields) {
		FieldDescriptor[] fields = new FieldDescriptor[5 + contentFields.length];
		fields[0] = fieldWithPath("data.content[]").description("페이지 콘텐츠 목록");
		fields[1] = fieldWithPath("data.page").description("현재 페이지 번호");
		fields[2] = fieldWithPath("data.size").description("페이지 크기");
		fields[3] = fieldWithPath("data.totalElements").description("전체 요소 수");
		fields[4] = fieldWithPath("data.totalPages").description("전체 페이지 수");
		System.arraycopy(contentFields, 0, fields, 5, contentFields.length);
		return fields;
	}

	private static FieldDescriptor[] userDetailFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "userId").description("사용자 ID"),
			fieldWithPath(prefix + "name").description("사용자 이름"),
			fieldWithPath(prefix + "email").description("사용자 이메일"),
			fieldWithPath(prefix + "role").description("전역 역할"),
			fieldWithPath(prefix + "isActive").description("사용자 활성 여부"),
			fieldWithPath(prefix + "campuses[]").description("소속 캠퍼스 목록"),
			fieldWithPath(prefix + "campuses[].membershipId").description("캠퍼스 멤버십 ID"),
			fieldWithPath(prefix + "campuses[].campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "campuses[].campusName").description("캠퍼스 이름"),
			fieldWithPath(prefix + "campuses[].region").optional().description("캠퍼스 지역"),
			fieldWithPath(prefix + "campuses[].campusRole").description("캠퍼스 내부 역할"),
			fieldWithPath(prefix + "campuses[].status").description("캠퍼스 멤버십 상태")
		};
	}

	private static FieldDescriptor[] campusDetailFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "name").description("캠퍼스 이름"),
			fieldWithPath(prefix + "region").optional().description("캠퍼스 지역"),
			fieldWithPath(prefix + "description").optional().description("캠퍼스 설명"),
			fieldWithPath(prefix + "isActive").description("캠퍼스 활성 여부"),
			fieldWithPath(prefix + "inviteCode").optional().description("조회 권한이 있을 때 노출되는 초대코드"),
			fieldWithPath(prefix + "myCampusRole").optional().description("현재 사용자의 캠퍼스 내부 역할. ADMIN이 멤버가 아니면 null"),
			fieldWithPath(prefix + "membershipStatus").optional().description("현재 사용자의 캠퍼스 멤버십 상태. ADMIN이 멤버가 아니면 null")
		};
	}

	private static FieldDescriptor[] campusMemberFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "membershipId").description("캠퍼스 멤버십 ID"),
			fieldWithPath(prefix + "campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "userId").description("사용자 ID"),
			fieldWithPath(prefix + "name").description("사용자 이름"),
			fieldWithPath(prefix + "email").description("사용자 이메일"),
			fieldWithPath(prefix + "campusRole").description("캠퍼스 내부 역할"),
			fieldWithPath(prefix + "status").description("캠퍼스 멤버십 상태")
		};
	}

	private static FieldDescriptor[] dutyAssignmentFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "assignmentId").description("담당 배정 ID"),
			fieldWithPath(prefix + "campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "userId").description("담당 사용자 ID"),
			fieldWithPath(prefix + "name").description("담당 사용자 이름"),
			fieldWithPath(prefix + "email").description("담당 사용자 이메일"),
			fieldWithPath(prefix + "dutyType").description("담당 유형. `COFFEE` 또는 `MEAL`"),
			fieldWithPath(prefix + "isActive").description("담당 배정 활성 여부"),
			fieldWithPath(prefix + "assignedAt").description("담당 지정 시각")
		};
	}
}
