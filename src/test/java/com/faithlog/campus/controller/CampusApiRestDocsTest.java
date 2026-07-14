package com.faithlog.campus.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.infrastructure.repository.PaymentAccountRepository;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@ActiveProfiles("test")
class CampusApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private PaymentAccountRepository paymentAccountRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Test
	void documents_campus_create_join_me_and_detail_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-campus-manager@example.com", UserRole.MANAGER);

		String createBody = mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "30캠",
					  "region": "분당",
					  "description": "분당 30캠퍼스"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.inviteCode").isString())
			.andExpect(jsonPath("$.data.myCampusRole").value("MINISTER"))
			.andExpect(jsonPath("$.data.membershipStatus").value("ACTIVE"))
			.andDo(document("campus-create-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				requestFields(
					fieldWithPath("name").description("생성할 캠퍼스 이름"),
					fieldWithPath("region").optional().description("캠퍼스 지역"),
					fieldWithPath("description").optional().description("캠퍼스 설명")
				),
				responseFields(apiResponseFields(
					fieldWithPath("data.campusId").description("생성된 캠퍼스 ID"),
					fieldWithPath("data.name").description("캠퍼스 이름"),
					fieldWithPath("data.region").optional().description("캠퍼스 지역"),
					fieldWithPath("data.description").optional().description("캠퍼스 설명"),
					fieldWithPath("data.inviteCode").description("생성된 초대코드"),
					fieldWithPath("data.myCampusRole").description("생성자의 캠퍼스 내부 역할. `MINISTER`"),
					fieldWithPath("data.membershipStatus").description("생성자의 캠퍼스 멤버십 상태. `ACTIVE`")
				))
			))
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode campus = objectMapper.readTree(createBody).path("data");
		long campusId = campus.path("campusId").asLong();
		String inviteCode = campus.path("inviteCode").asText();
		String memberToken = signupAndLogin("docs-campus-member@example.com", UserRole.USER);

		mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "%s"
					}
					""".formatted(inviteCode)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.campusRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"))
			.andDo(document("campus-join-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				requestFields(
					fieldWithPath("inviteCode").description("가입할 캠퍼스 초대코드")
				),
				responseFields(apiResponseFields(membershipFields("data.")))
			));

		mockMvc.perform(get("/api/v1/campuses/me")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].campusId").value(campusId))
			.andDo(document("campuses-me-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				responseFields(apiResponseFields(
					fieldWithPath("data[]").description("현재 사용자의 ACTIVE 캠퍼스 멤버십 목록"),
					fieldWithPath("data[].membershipId").description("캠퍼스 멤버십 ID"),
					fieldWithPath("data[].campusId").description("캠퍼스 ID"),
					fieldWithPath("data[].campusName").description("캠퍼스 이름"),
					fieldWithPath("data[].region").optional().description("캠퍼스 지역"),
					fieldWithPath("data[].campusRole").description("현재 사용자의 캠퍼스 내부 역할"),
					fieldWithPath("data[].status").description("멤버십 상태. MVP에서는 ACTIVE만 반환")
				))
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inviteCode").doesNotExist())
			.andExpect(jsonPath("$.data.myCampusRole").value("MEMBER"))
			.andDo(document("campus-detail-member-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				responseFields(apiResponseFields(campusDetailFields(false)))
			));

		String adminToken = signupAndLogin("docs-campus-admin@example.com", UserRole.ADMIN);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
			.andExpect(jsonPath("$.data.myCampusRole").value(nullValue()))
			.andExpect(jsonPath("$.data.membershipStatus").value(nullValue()))
			.andDo(document("campus-detail-admin-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				responseFields(apiResponseFields(campusDetailFields(true)))
			));
	}

	@Test
	void documents_campus_join_invalid_invite_code() throws Exception {
		String memberToken = signupAndLogin("docs-campus-invalid-invite@example.com", UserRole.USER);

		mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "NO-SUCH-CODE"
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("CAMPUS_INVALID_INVITE_CODE"))
			.andExpect(jsonPath("$.message").value("유효하지 않은 초대코드입니다."))
			.andDo(document("campus-join-invalid-invite-code",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				requestFields(
					fieldWithPath("inviteCode").description("존재하지 않는 초대코드")
				),
				responseFields(errorResponseFields())
			));
	}

	@Test
	void documents_campus_member_delete_success() throws Exception {
		String managerToken = signupAndLogin("docs-campus-delete-manager@example.com", UserRole.MANAGER);
		String createBody = mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "31캠",
					  "region": "분당",
					  "description": "분당 31캠퍼스"
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode campus = objectMapper.readTree(createBody).path("data");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-campus-delete-member@example.com", UserRole.USER);
		String joinBody = mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "%s"
					}
					""".formatted(campus.path("inviteCode").asText())))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long membershipId = objectMapper.readTree(joinBody).path("data").path("membershipId").asLong();

		mockMvc.perform(delete("/api/v1/campuses/{campusId}/members/{membershipId}", campusId, membershipId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isNoContent())
			.andDo(document("campus-member-delete-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("membershipId").description("삭제할 캠퍼스 멤버십 ID")
				)
			));
	}

	@Test
	void documents_admin_campus_role_change_success() throws Exception {
		String managerToken = signupAndLogin("docs-role-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampusForDocs(managerToken, "32캠");
		long campusId = campus.path("campusId").asLong();
		String elderToken = signupAndLogin("docs-role-elder@example.com", UserRole.USER);
		User elder = userRepository.findByEmail("docs-role-elder@example.com").orElseThrow();
		joinCampusForDocs(elderToken, campus.path("inviteCode").asText());
		updateCampusRole(campusId, elder.id(), CampusRole.ELDER);
		String memberToken = signupAndLogin("docs-role-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("docs-role-member@example.com").orElseThrow();
		JsonNode memberMembership = joinCampusForDocs(memberToken, campus.path("inviteCode").asText());

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/members", campusId)
				.header("Authorization", "Bearer " + elderToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andDo(document("admin-campus-members-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID")
				),
				responseFields(apiResponseFields(
					fieldWithPath("data[]").description("캠퍼스 ACTIVE 멤버 목록"),
					fieldWithPath("data[].membershipId").description("캠퍼스 멤버십 ID"),
					fieldWithPath("data[].campusId").description("캠퍼스 ID"),
					fieldWithPath("data[].userId").description("사용자 ID"),
					fieldWithPath("data[].name").description("사용자 이름"),
					fieldWithPath("data[].email").description("사용자 이메일"),
					fieldWithPath("data[].campusRole").description("캠퍼스 내부 역할"),
					fieldWithPath("data[].status").description("캠퍼스 멤버십 상태")
				))
			));

		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role",
				campusId,
				memberMembership.path("membershipId").asLong())
				.header("Authorization", "Bearer " + elderToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "campusRole": "ELDER"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.campusRole").value("ELDER"))
			.andDo(document("admin-campus-role-change-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("campusMemberId").description("역할을 변경할 campus_members.id")
				),
				requestFields(
					fieldWithPath("campusRole").description("새 캠퍼스 역할. `MINISTER`, `ELDER`, `CAMPUS_LEADER`, `MEMBER`")
				),
				responseFields(apiResponseFields(adminCampusMemberFields("data.")))
			));
	}

	@Test
	void documents_admin_coffee_duty_assignment_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-coffee-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampusForDocs(managerToken, "33캠");
		long campusId = campus.path("campusId").asLong();
		String leaderToken = signupAndLogin("docs-coffee-leader@example.com", UserRole.USER);
		User leader = userRepository.findByEmail("docs-coffee-leader@example.com").orElseThrow();
		joinCampusForDocs(leaderToken, campus.path("inviteCode").asText());
		updateCampusRole(campusId, leader.id(), CampusRole.CAMPUS_LEADER);
		String memberToken = signupAndLogin("docs-coffee-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("docs-coffee-member@example.com").orElseThrow();
		joinCampusForDocs(memberToken, campus.path("inviteCode").asText());

		String assignBody = mockMvc.perform(put("/api/v1/admin/campuses/{campusId}/duty-assignments/coffee", campusId)
				.header("Authorization", "Bearer " + leaderToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userId": %d
					}
					""".formatted(member.id())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.dutyType").value("COFFEE"))
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andDo(document("admin-coffee-duty-assign-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID")
				),
				requestFields(
					fieldWithPath("userId").description("커피 담당자로 지정할 캠퍼스 멤버의 사용자 ID")
				),
				responseFields(apiResponseFields(dutyAssignmentFields("data.")))
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long assignmentId = objectMapper.readTree(assignBody).path("data").path("assignmentId").asLong();

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/duty-assignments", campusId)
				.header("Authorization", "Bearer " + leaderToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andDo(document("admin-duty-assignments-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID")
				),
				responseFields(apiResponseFields(
					fieldWithPath("data[]").description("캠퍼스의 활성 담당자 배정 목록"),
					fieldWithPath("data[].assignmentId").description("담당자 배정 ID"),
					fieldWithPath("data[].campusId").description("캠퍼스 ID"),
					fieldWithPath("data[].userId").description("담당자 사용자 ID"),
					fieldWithPath("data[].name").description("담당자 이름"),
					fieldWithPath("data[].email").description("담당자 이메일"),
					fieldWithPath("data[].dutyType").description("담당 유형"),
					fieldWithPath("data[].isActive").description("활성 여부"),
					fieldWithPath("data[].assignedAt").description("담당 지정 시각")
				))
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/duty-assignments/me", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.dutyType").value("COFFEE"))
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andDo(document("my-coffee-duty-assignment-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID")
				),
				responseFields(apiResponseFields(myDutyAssignmentFields("data.")))
			));

		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}",
				campusId,
				assignmentId)
				.header("Authorization", "Bearer " + leaderToken))
			.andExpect(status().isNoContent())
			.andDo(document("admin-coffee-duty-revoke-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("assignmentId").description("해제할 커피 담당자 배정 ID")
				)
			));
	}

	@Test
	void documents_meal_duty_assignment_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-meal-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampusForDocs(managerToken, "189문서밥캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-meal-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("docs-meal-member@example.com").orElseThrow();
		joinCampusForDocs(memberToken, campus.path("inviteCode").asText());

		String body = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/duty-assignments/meal", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"userId": %d}
					""".formatted(member.id())))
			.andExpect(status().isOk())
			.andDo(document("admin-meal-duty-assign-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				requestFields(fieldWithPath("userId").description("밥 담당자로 지정할 ACTIVE 캠퍼스 멤버 사용자 ID")),
				responseFields(apiResponseFields(dutyAssignmentFields("data.")))
			))
			.andReturn().getResponse().getContentAsString();
		long assignmentId = objectMapper.readTree(body).path("data").path("assignmentId").asLong();

		mockMvc.perform(get("/api/v1/campuses/{campusId}/duty-assignments/me/meal", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andDo(document("my-meal-duty-assignment-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				responseFields(apiResponseFields(myDutyAssignmentFields("data.")))
			));

		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/duty-assignments/meal/{assignmentId}",
				campusId, assignmentId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isNoContent())
			.andDo(document("admin-meal-duty-revoke-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("assignmentId").description("해제할 MEAL duty assignment ID")
				)
			));
	}

	@Test
	@Transactional
	void documents_coffee_duty_revoke_unpaid_conflict() throws Exception {
		documentDutyRevokeUnpaidConflict(PaymentCategory.COFFEE);
	}

	@Test
	@Transactional
	void documents_meal_duty_revoke_unpaid_conflict() throws Exception {
		documentDutyRevokeUnpaidConflict(PaymentCategory.MEAL);
	}

	private void documentDutyRevokeUnpaidConflict(PaymentCategory category) throws Exception {
		String prefix = category == PaymentCategory.COFFEE ? "coffee" : "meal";
		String managerToken = signupAndLogin("docs-" + prefix + "-conflict-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampusForDocs(managerToken, "200" + prefix + "미납해제충돌캠");
		long campusId = campus.path("campusId").asLong();
		String dutyToken = signupAndLogin("docs-" + prefix + "-conflict-duty@example.com", UserRole.USER);
		User duty = userRepository.findByEmail("docs-" + prefix + "-conflict-duty@example.com").orElseThrow();
		joinCampusForDocs(dutyToken, campus.path("inviteCode").asText());
		String assignPath = category == PaymentCategory.COFFEE
			? "/api/v1/admin/campuses/{campusId}/duty-assignments/coffee"
			: "/api/v1/admin/campuses/{campusId}/duty-assignments/meal";
		var assignRequest = category == PaymentCategory.COFFEE
			? put(assignPath, campusId)
			: post(assignPath, campusId);
		String assignmentBody = mockMvc.perform(assignRequest
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":" + duty.id() + "}"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		long assignmentId = objectMapper.readTree(assignmentBody).path("data").path("assignmentId").asLong();
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campusId, category, prefix + " 미납 계좌", "하나은행", "200-" + prefix + "-conflict",
			"담당자", duty.id()));
		chargeItemRepository.saveAndFlush(ChargeItem.create(
			campusId, duty.id(), category, account.id(), account.bankName(), account.accountNumber(),
			account.accountHolder(), ChargeSourceType.POLL_RESPONSE, assignmentId,
			prefix + " 미납", "담당 해제 충돌 문서", 5000, null));
		String revokePath = "/api/v1/admin/campuses/{campusId}/duty-assignments/" + prefix + "/{assignmentId}";
		String code = category == PaymentCategory.COFFEE
			? "CAMPUS_COFFEE_DUTY_UNPAID_CHARGE_CONFLICT"
			: "CAMPUS_MEAL_DUTY_UNPAID_CHARGE_CONFLICT";

		mockMvc.perform(delete(revokePath, campusId, assignmentId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(code))
			.andDo(document(
				"admin-" + prefix + "-duty-revoke-unpaid-conflict",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("assignmentId").description("해제할 담당자 배정 ID")
				),
				responseFields(errorResponseFields())
			));
	}

	private String signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "문서회원",
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

	private JsonNode createCampusForDocs(String accessToken, String name) throws Exception {
		String createBody = mockMvc.perform(post("/api/v1/campuses")
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
		return objectMapper.readTree(createBody).path("data");
	}

	private JsonNode joinCampusForDocs(String accessToken, String inviteCode) throws Exception {
		String joinBody = mockMvc.perform(post("/api/v1/campuses/join")
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
		return objectMapper.readTree(joinBody).path("data");
	}

	private void updateCampusRole(long campusId, long userId, CampusRole campusRole) {
		CampusMember member = campusMemberRepository.findByCampusIdAndUserId(campusId, userId).orElseThrow();
		ReflectionTestUtils.setField(member, "campusRole", campusRole);
		campusMemberRepository.saveAndFlush(member);
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

	private static FieldDescriptor[] membershipFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "membershipId").description("캠퍼스 멤버십 ID"),
			fieldWithPath(prefix + "campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "campusName").description("캠퍼스 이름"),
			fieldWithPath(prefix + "region").optional().description("캠퍼스 지역"),
			fieldWithPath(prefix + "campusRole").description("현재 사용자의 캠퍼스 내부 역할"),
			fieldWithPath(prefix + "status").description("멤버십 상태")
		};
	}

	private static FieldDescriptor[] adminCampusMemberFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "membershipId").description("캠퍼스 멤버십 ID"),
			fieldWithPath(prefix + "campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "userId").description("사용자 ID"),
			fieldWithPath(prefix + "name").description("사용자 이름"),
			fieldWithPath(prefix + "email").description("사용자 이메일"),
			fieldWithPath(prefix + "campusRole").description("변경된 캠퍼스 내부 역할"),
			fieldWithPath(prefix + "status").description("캠퍼스 멤버십 상태")
		};
	}

	private static FieldDescriptor[] dutyAssignmentFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "assignmentId").description("담당자 배정 ID"),
			fieldWithPath(prefix + "campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "userId").description("담당자 사용자 ID"),
			fieldWithPath(prefix + "name").description("담당자 이름"),
			fieldWithPath(prefix + "email").description("담당자 이메일"),
			fieldWithPath(prefix + "dutyType").description("담당 유형"),
			fieldWithPath(prefix + "isActive").description("활성 여부"),
			fieldWithPath(prefix + "assignedAt").description("담당 지정 시각")
		};
	}

	private static FieldDescriptor[] myDutyAssignmentFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "userId").description("현재 사용자 ID"),
			fieldWithPath(prefix + "campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "dutyType").description("담당 유형. 현재는 `COFFEE`"),
			fieldWithPath(prefix + "isActive").description("현재 사용자가 해당 캠퍼스의 활성 COFFEE 담당자인지 여부")
		};
	}

	private static FieldDescriptor[] campusDetailFields(boolean includeInviteCode) {
		FieldDescriptor[] commonFields = new FieldDescriptor[] {
			fieldWithPath("data.campusId").description("캠퍼스 ID"),
			fieldWithPath("data.name").description("캠퍼스 이름"),
			fieldWithPath("data.region").optional().description("캠퍼스 지역"),
			fieldWithPath("data.description").optional().description("캠퍼스 설명"),
			fieldWithPath("data.isActive").description("캠퍼스 활성 여부"),
			fieldWithPath("data.myCampusRole").optional().description("현재 사용자의 캠퍼스 내부 역할. ADMIN이 멤버가 아니면 null"),
			fieldWithPath("data.membershipStatus").optional().description("현재 사용자의 캠퍼스 멤버십 상태. ADMIN이 멤버가 아니면 null")
		};
		if (!includeInviteCode) {
			return commonFields;
		}

		FieldDescriptor[] fields = new FieldDescriptor[commonFields.length + 1];
		System.arraycopy(commonFields, 0, fields, 0, commonFields.length);
		fields[commonFields.length] = fieldWithPath("data.inviteCode").description("조회 권한이 있을 때 노출되는 초대코드");
		return fields;
	}

	private static FieldDescriptor[] errorResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 실패 응답에서는 false"),
			fieldWithPath("code").description("오류 코드"),
			fieldWithPath("message").description("오류 메시지"),
			fieldWithPath("data").optional().description("실패 응답에서는 null"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
	}
}
