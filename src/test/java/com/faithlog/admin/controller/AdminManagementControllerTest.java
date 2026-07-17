package com.faithlog.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusDutyAssignmentRepository;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
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
class AdminManagementControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private CampusDutyAssignmentRepository campusDutyAssignmentRepository;

	@Test
	void admin_users_api_requires_service_admin_and_supports_search_role_paging_and_sort() throws Exception {
		String adminToken = signupAndLogin("admin-users-admin@example.com", UserRole.ADMIN, "서비스관리자");
		String managerToken = signupAndLogin("admin-users-manager@example.com", UserRole.MANAGER, "서비스매니저");
		String userToken = signupAndLogin("admin-users-user@example.com", UserRole.USER, "일반사용자");
		User manager = userRepository.findByEmail("admin-users-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "관리캠");
		joinCampus(userToken, campus.path("inviteCode").asText());

		mockMvc.perform(get("/api/v1/admin/users")
				.header("Authorization", "Bearer " + userToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_ACCESS_FORBIDDEN"));
		mockMvc.perform(get("/api/v1/admin/users")
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_ACCESS_FORBIDDEN"));

		mockMvc.perform(get("/api/v1/admin/users")
				.header("Authorization", "Bearer " + adminToken)
				.param("name", "서비스")
				.param("email", "manager")
				.param("userId", manager.id().toString())
				.param("role", "MANAGER")
				.param("page", "0")
				.param("size", "10")
				.param("sort", "email,asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].userId").value(manager.id()))
			.andExpect(jsonPath("$.data.content[0].name").value("서비스매니저"))
			.andExpect(jsonPath("$.data.content[0].email").value("admin-users-manager@example.com"))
			.andExpect(jsonPath("$.data.content[0].role").value("MANAGER"))
			.andExpect(jsonPath("$.data.content[0].campusCount").value(1))
			.andExpect(jsonPath("$.data.content[0].campuses[0].campusName").value("관리캠"))
			.andExpect(jsonPath("$.data.content[0].campuses[0].campusRole").value("MINISTER"))
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(10))
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.totalPages").value(1));
	}

	@Test
	void admin_user_detail_role_change_and_last_admin_guard_follow_contract() throws Exception {
		String adminToken = signupAndLogin("admin-role-admin@example.com", UserRole.ADMIN, "서비스관리자");
		String secondAdminToken = signupAndLogin("admin-role-second@example.com", UserRole.ADMIN, "두번째관리자");
		String memberToken = signupAndLogin("admin-role-member@example.com", UserRole.USER, "소속회원");
		User admin = userRepository.findByEmail("admin-role-admin@example.com").orElseThrow();
		User member = userRepository.findByEmail("admin-role-member@example.com").orElseThrow();
		JsonNode campus = createCampus(secondAdminToken, "상세캠");
		JsonNode membership = joinCampus(memberToken, campus.path("inviteCode").asText());
		updateCampusRole(membership.path("membershipId").asLong(), CampusRole.ELDER);

		mockMvc.perform(get("/api/v1/admin/users/{userId}", member.id())
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.name").value("소속회원"))
			.andExpect(jsonPath("$.data.email").value("admin-role-member@example.com"))
			.andExpect(jsonPath("$.data.role").value("USER"))
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andExpect(jsonPath("$.data.campuses[0].membershipId").value(membership.path("membershipId").asLong()))
			.andExpect(jsonPath("$.data.campuses[0].campusRole").value("ELDER"))
			.andExpect(jsonPath("$.data.campuses[0].status").value("ACTIVE"));

		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", member.id())
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "role": "MANAGER"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.role").value("MANAGER"));

		assertThat(userRepository.findById(member.id()).orElseThrow().role()).isEqualTo(UserRole.MANAGER);
		assertThat(campusMemberRepository.findById(membership.path("membershipId").asLong()).orElseThrow().campusRole())
			.isEqualTo(CampusRole.ELDER);

		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", admin.id())
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "role": "USER"
					}
					"""))
			.andExpect(status().isOk());
		demoteOtherAdminsToUser(userRepository.findByEmail("admin-role-second@example.com").orElseThrow().id());

		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", userRepository.findByEmail("admin-role-second@example.com").orElseThrow().id())
				.header("Authorization", "Bearer " + secondAdminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "role": "USER"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ADMIN_LAST_ADMIN_DEMOTION_FORBIDDEN"));
	}

	@Test
	void service_admin_direct_member_add_rejects_stale_active_duty_reactivation() throws Exception {
		String adminToken = signupAndLogin("admin-stale-add-admin@example.com", UserRole.ADMIN, "서비스관리자");
		String managerToken = signupAndLogin("admin-stale-add-manager@example.com", UserRole.MANAGER, "캠퍼스관리자");
		String memberToken = signupAndLogin("admin-stale-add-member@example.com", UserRole.USER, "과거담당자");
		User member = userRepository.findByEmail("admin-stale-add-member@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "직접추가복구캠");
		JsonNode membership = joinCampus(memberToken, campus.path("inviteCode").asText());
		campusDutyAssignmentRepository.saveAndFlush(CampusDutyAssignment.assignCoffee(
			campus.path("campusId").asLong(), member.id()
		));
		CampusMember inactive = campusMemberRepository
			.findById(membership.path("membershipId").asLong())
			.orElseThrow();
		inactive.deactivate();
		campusMemberRepository.saveAndFlush(inactive);

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/members", campus.path("campusId").asLong())
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userId": %d
					}
					""".formatted(member.id())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CAMPUS_MEMBER_ACTIVE_DUTY_CONFLICT"));
		assertThat(campusMemberRepository.findById(inactive.id())).get().matches(value -> !value.isActive());
	}

	@Test
	void admin_campus_list_add_member_reactivate_duplicate_and_delete_membership_follow_contract() throws Exception {
		String adminToken = signupAndLogin("admin-campus-admin@example.com", UserRole.ADMIN, "서비스관리자");
		String managerToken = signupAndLogin("admin-campus-manager@example.com", UserRole.MANAGER, "캠퍼스관리자");
		String memberToken = signupAndLogin("admin-campus-member@example.com", UserRole.USER, "직접추가회원");
		User member = userRepository.findByEmail("admin-campus-member@example.com").orElseThrow();
		JsonNode activeCampus = createCampus(managerToken, "활성캠");
		JsonNode pausedCampus = createCampus(managerToken, "중지캠");
		long pausedCampusId = pausedCampus.path("campusId").asLong();

		mockMvc.perform(patch("/api/v1/campuses/{campusId}", pausedCampusId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "isActive": false
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.isActive").value(false));

		mockMvc.perform(get("/api/v1/admin/campuses")
				.header("Authorization", "Bearer " + adminToken)
				.param("name", "중지")
				.param("region", "분당")
				.param("status", "PAUSED")
				.param("page", "0")
				.param("size", "10")
				.param("sort", "name,asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].campusId").value(pausedCampusId))
			.andExpect(jsonPath("$.data.content[0].status").value("PAUSED"))
			.andExpect(jsonPath("$.data.content[0].isActive").value(false))
			.andExpect(jsonPath("$.data.content[0].memberCount").value(1))
			.andExpect(jsonPath("$.data.content[0].adminCount").value(1));

		String addBody = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/members", activeCampus.path("campusId").asLong())
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userId": %d
					}
					""".formatted(member.id())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.campusRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long membershipId = objectMapper.readTree(addBody).path("data").path("membershipId").asLong();

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/members", activeCampus.path("campusId").asLong())
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userId": %d
					}
					""".formatted(member.id())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CAMPUS_ALREADY_JOINED"));

		mockMvc.perform(delete("/api/v1/campuses/{campusId}/members/{membershipId}", activeCampus.path("campusId").asLong(), membershipId)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isNoContent());
		assertThat(campusMemberRepository.findById(membershipId).orElseThrow().status()).isEqualTo(CampusMemberStatus.INACTIVE);

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/members", activeCampus.path("campusId").asLong())
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "userId": %d
					}
					""".formatted(member.id())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.membershipId").value(membershipId))
			.andExpect(jsonPath("$.data.campusRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}

	@Test
	void admin_validates_paging_and_sort_contracts() throws Exception {
		String adminToken = signupAndLogin("admin-validation-admin@example.com", UserRole.ADMIN, "서비스관리자");

		mockMvc.perform(get("/api/v1/admin/users")
				.header("Authorization", "Bearer " + adminToken)
				.param("sort", "campusCount,desc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ADMIN_INVALID_SORT_PROPERTY"));

		mockMvc.perform(get("/api/v1/admin/campuses")
				.header("Authorization", "Bearer " + adminToken)
				.param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ADMIN_INVALID_SIZE"));
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

	private void demoteOtherAdminsToUser(long remainingAdminId) {
		userRepository.findAll()
			.stream()
			.filter(user -> user.role() == UserRole.ADMIN)
			.filter(user -> !user.id().equals(remainingAdminId))
			.forEach(user -> {
				ReflectionTestUtils.setField(user, "role", UserRole.USER);
				userRepository.save(user);
			});
		userRepository.flush();
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
}
