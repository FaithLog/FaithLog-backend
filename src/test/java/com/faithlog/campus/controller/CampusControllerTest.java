package com.faithlog.campus.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import org.hamcrest.Matchers;
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
class CampusControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Test
	void create_campus_requires_manager_or_admin_and_returns_invite_code_and_creator_membership() throws Exception {
		String userToken = signupAndLogin("campus-user@example.com", UserRole.USER);

		mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + userToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "10캠",
					  "region": "분당",
					  "description": "분당 10캠퍼스"
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("CAMPUS_CREATE_FORBIDDEN"))
			.andExpect(jsonPath("$.message").value("캠퍼스 생성 권한이 없습니다."));

		String managerToken = signupAndLogin("campus-manager@example.com", UserRole.MANAGER);

		mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "10캠",
					  "region": "분당",
					  "description": "분당 10캠퍼스"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.data.campusId").isNumber())
			.andExpect(jsonPath("$.data.name").value("10캠"))
			.andExpect(jsonPath("$.data.region").value("분당"))
			.andExpect(jsonPath("$.data.description").value("분당 10캠퍼스"))
			.andExpect(jsonPath("$.data.inviteCode").isString())
			.andExpect(jsonPath("$.data.myCampusRole").value("MINISTER"))
			.andExpect(jsonPath("$.data.membershipStatus").value("ACTIVE"));

		String adminToken = signupAndLogin("campus-admin@example.com", UserRole.ADMIN);

		mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "10-관리캠",
					  "region": "분당",
					  "description": "어드민 생성 캠퍼스"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.name").value("10-관리캠"))
			.andExpect(jsonPath("$.data.inviteCode").isString())
			.andExpect(jsonPath("$.data.myCampusRole").value("MINISTER"))
			.andExpect(jsonPath("$.data.membershipStatus").value("ACTIVE"));
	}

	@Test
	void join_by_invite_code_creates_active_member_and_rejects_duplicate_join() throws Exception {
		String managerToken = signupAndLogin("join-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "11캠");
		String inviteCode = campus.path("inviteCode").asText();
		String memberToken = signupAndLogin("join-member@example.com", UserRole.USER);

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
			.andExpect(jsonPath("$.data.membershipId").isNumber())
			.andExpect(jsonPath("$.data.campusId").value(campus.path("campusId").asLong()))
			.andExpect(jsonPath("$.data.campusName").value("11캠"))
			.andExpect(jsonPath("$.data.region").value("분당"))
			.andExpect(jsonPath("$.data.campusRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "%s"
					}
					""".formatted(inviteCode)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("CAMPUS_ALREADY_JOINED"))
			.andExpect(jsonPath("$.message").value("이미 가입된 캠퍼스입니다."));
	}

	@Test
	void join_rejects_unknown_invite_code() throws Exception {
		String memberToken = signupAndLogin("unknown-invite@example.com", UserRole.USER);

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
			.andExpect(jsonPath("$.message").value("유효하지 않은 초대코드입니다."));
	}

	@Test
	void me_returns_only_current_active_memberships() throws Exception {
		String managerToken = signupAndLogin("me-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "12캠");
		String memberToken = signupAndLogin("me-member@example.com", UserRole.USER);
		joinCampus(memberToken, campus.path("inviteCode").asText());

		mockMvc.perform(get("/api/v1/campuses/me")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].membershipId").isNumber())
			.andExpect(jsonPath("$.data[0].campusId").value(campus.path("campusId").asLong()))
			.andExpect(jsonPath("$.data[0].campusName").value("12캠"))
			.andExpect(jsonPath("$.data[0].region").value("분당"))
			.andExpect(jsonPath("$.data[0].campusRole").value("MEMBER"))
			.andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
			.andExpect(jsonPath("$.data[0].joinedAt").doesNotExist());
	}

	@Test
	void detail_hides_invite_code_from_member_and_shows_it_to_admin_even_without_membership() throws Exception {
		String managerToken = signupAndLogin("detail-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "13캠");
		long campusId = campus.path("campusId").asLong();
		String inviteCode = campus.path("inviteCode").asText();
		String memberToken = signupAndLogin("detail-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("detail-member@example.com").orElseThrow();
		joinCampus(memberToken, inviteCode);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campusId").value(campusId))
			.andExpect(jsonPath("$.data.name").value("13캠"))
			.andExpect(jsonPath("$.data.inviteCode").doesNotExist())
			.andExpect(jsonPath("$.data.myCampusRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.membershipStatus").value("ACTIVE"));

		updateCampusRole(campusId, member.id(), CampusRole.ELDER);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
			.andExpect(jsonPath("$.data.myCampusRole").value("ELDER"));

		updateCampusRole(campusId, member.id(), CampusRole.CAMPUS_LEADER);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
			.andExpect(jsonPath("$.data.myCampusRole").value("CAMPUS_LEADER"));

		String adminToken = signupAndLogin("detail-admin@example.com", UserRole.ADMIN);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campusId").value(campusId))
			.andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
			.andExpect(jsonPath("$.data.myCampusRole").value(Matchers.nullValue()))
			.andExpect(jsonPath("$.data.membershipStatus").value(Matchers.nullValue()));
	}

	@Test
	void detail_rejects_non_member_non_admin() throws Exception {
		String managerToken = signupAndLogin("forbidden-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "14캠");
		String outsiderToken = signupAndLogin("forbidden-outsider@example.com", UserRole.USER);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campus.path("campusId").asLong())
				.header("Authorization", "Bearer " + outsiderToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("CAMPUS_VIEW_FORBIDDEN"))
			.andExpect(jsonPath("$.message").value("캠퍼스 조회 권한이 없습니다."));
	}

	@Test
	void delete_member_requires_campus_management_role_and_deactivates_membership() throws Exception {
		String managerToken = signupAndLogin("delete-http-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "15캠");
		long campusId = campus.path("campusId").asLong();
		String inviteCode = campus.path("inviteCode").asText();
		String elderToken = signupAndLogin("delete-http-elder@example.com", UserRole.USER);
		User elder = userRepository.findByEmail("delete-http-elder@example.com").orElseThrow();
		JsonNode elderMembership = joinCampus(elderToken, inviteCode);
		updateCampusRole(campusId, elder.id(), CampusRole.ELDER);
		String memberToken = signupAndLogin("delete-http-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("delete-http-member@example.com").orElseThrow();
		JsonNode memberMembership = joinCampus(memberToken, inviteCode);

		mockMvc.perform(delete("/api/v1/campuses/{campusId}/members/{membershipId}", campusId, elderMembership.path("membershipId").asLong())
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("CAMPUS_MEMBER_MANAGE_FORBIDDEN"))
			.andExpect(jsonPath("$.message").value("캠퍼스 멤버 관리 권한이 없습니다."));

		mockMvc.perform(delete("/api/v1/campuses/{campusId}/members/{membershipId}", campusId, memberMembership.path("membershipId").asLong())
				.header("Authorization", "Bearer " + elderToken))
			.andExpect(status().isNoContent());

		CampusMember deletedMember = campusMemberRepository.findByCampusIdAndUserId(campusId, member.id()).orElseThrow();
		assertThat(deletedMember.status()).isEqualTo(CampusMemberStatus.INACTIVE);
	}

	@Test
	void delete_member_allows_service_admin_without_campus_membership() throws Exception {
		String managerToken = signupAndLogin("delete-http-admin-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "16캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("delete-http-admin-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("delete-http-admin-member@example.com").orElseThrow();
		JsonNode memberMembership = joinCampus(memberToken, campus.path("inviteCode").asText());
		String adminToken = signupAndLogin("delete-http-admin@example.com", UserRole.ADMIN);

		mockMvc.perform(delete("/api/v1/campuses/{campusId}/members/{membershipId}", campusId, memberMembership.path("membershipId").asLong())
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isNoContent());

		CampusMember deletedMember = campusMemberRepository.findByCampusIdAndUserId(campusId, member.id()).orElseThrow();
		assertThat(deletedMember.status()).isEqualTo(CampusMemberStatus.INACTIVE);
	}

	@Test
	void admin_change_campus_role_maps_request_and_response() throws Exception {
		String managerToken = signupAndLogin("role-http-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("role-http-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "17캠");
		long campusId = campus.path("campusId").asLong();
		CampusMember ministerMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, manager.id()).orElseThrow();
		String elderToken = signupAndLogin("role-http-elder@example.com", UserRole.USER);
		User elder = userRepository.findByEmail("role-http-elder@example.com").orElseThrow();
		JsonNode elderMembership = joinCampus(elderToken, campus.path("inviteCode").asText());
		updateCampusRole(campusId, elder.id(), CampusRole.ELDER);
		String memberToken = signupAndLogin("role-http-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("role-http-member@example.com").orElseThrow();
		JsonNode memberMembership = joinCampus(memberToken, campus.path("inviteCode").asText());

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
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.membershipId").value(memberMembership.path("membershipId").asLong()))
			.andExpect(jsonPath("$.data.campusId").value(campusId))
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.name").value("캠퍼스테스트"))
			.andExpect(jsonPath("$.data.email").value("role-http-member@example.com"))
			.andExpect(jsonPath("$.data.campusRole").value("ELDER"))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role",
				campusId,
				ministerMembership.id())
				.header("Authorization", "Bearer " + elderToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "campusRole": "MEMBER"
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CAMPUS_ROLE_HIERARCHY_FORBIDDEN"))
			.andExpect(jsonPath("$.message").value("상위 캠퍼스 역할은 변경할 수 없습니다."));
	}

	@Test
	void admin_coffee_duty_assignment_maps_request_response_list_and_delete() throws Exception {
		String managerToken = signupAndLogin("coffee-http-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "18캠");
		long campusId = campus.path("campusId").asLong();
		String leaderToken = signupAndLogin("coffee-http-leader@example.com", UserRole.USER);
		User leader = userRepository.findByEmail("coffee-http-leader@example.com").orElseThrow();
		joinCampus(leaderToken, campus.path("inviteCode").asText());
		updateCampusRole(campusId, leader.id(), CampusRole.CAMPUS_LEADER);
		String firstToken = signupAndLogin("coffee-http-first@example.com", UserRole.USER);
		User first = userRepository.findByEmail("coffee-http-first@example.com").orElseThrow();
		joinCampus(firstToken, campus.path("inviteCode").asText());
		String secondToken = signupAndLogin("coffee-http-second@example.com", UserRole.USER);
		User second = userRepository.findByEmail("coffee-http-second@example.com").orElseThrow();
		joinCampus(secondToken, campus.path("inviteCode").asText());

		mockMvc.perform(put("/api/v1/admin/campuses/{campusId}/duty-assignments/coffee", campusId)
				.header("Authorization", "Bearer " + leaderToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userId": %d
					}
					""".formatted(first.id())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(first.id()))
			.andExpect(jsonPath("$.data.dutyType").value("COFFEE"))
			.andExpect(jsonPath("$.data.isActive").value(true));

		String replaceBody = mockMvc.perform(put("/api/v1/admin/campuses/{campusId}/duty-assignments/coffee", campusId)
				.header("Authorization", "Bearer " + leaderToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userId": %d
					}
					""".formatted(second.id())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(second.id()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long assignmentId = objectMapper.readTree(replaceBody).path("data").path("assignmentId").asLong();

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/duty-assignments", campusId)
				.header("Authorization", "Bearer " + leaderToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].assignmentId").value(assignmentId))
			.andExpect(jsonPath("$.data[0].userId").value(second.id()));

		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}", campusId, assignmentId)
				.header("Authorization", "Bearer " + leaderToken))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/duty-assignments", campusId)
				.header("Authorization", "Bearer " + leaderToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void my_coffee_duty_assignment_returns_active_status_for_active_members() throws Exception {
		String managerToken = signupAndLogin("coffee-me-http-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "19캠");
		long campusId = campus.path("campusId").asLong();
		String dutyToken = signupAndLogin("coffee-me-http-duty@example.com", UserRole.USER);
		User duty = userRepository.findByEmail("coffee-me-http-duty@example.com").orElseThrow();
		joinCampus(dutyToken, campus.path("inviteCode").asText());
		String memberToken = signupAndLogin("coffee-me-http-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("coffee-me-http-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		String outsiderToken = signupAndLogin("coffee-me-http-outsider@example.com", UserRole.USER);

		mockMvc.perform(put("/api/v1/admin/campuses/{campusId}/duty-assignments/coffee", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userId": %d
					}
					""".formatted(duty.id())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/campuses/{campusId}/duty-assignments/me", campusId)
				.header("Authorization", "Bearer " + dutyToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(duty.id()))
			.andExpect(jsonPath("$.data.campusId").value(campusId))
			.andExpect(jsonPath("$.data.dutyType").value("COFFEE"))
			.andExpect(jsonPath("$.data.isActive").value(true));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/duty-assignments/me", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.campusId").value(campusId))
			.andExpect(jsonPath("$.data.dutyType").value("COFFEE"))
			.andExpect(jsonPath("$.data.isActive").value(false));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/duty-assignments/me", campusId)
				.header("Authorization", "Bearer " + outsiderToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CAMPUS_VIEW_FORBIDDEN"));
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

	private void updateCampusRole(long campusId, long userId, CampusRole campusRole) {
		CampusMember member = campusMemberRepository.findByCampusIdAndUserId(campusId, userId).orElseThrow();
		ReflectionTestUtils.setField(member, "campusRole", campusRole);
		campusMemberRepository.saveAndFlush(member);
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

	private String signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "캠퍼스테스트",
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
