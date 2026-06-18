package com.faithlog.campus.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class CampusControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

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
			.andExpect(jsonPath("$.code").value("FORBIDDEN"))
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
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
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
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
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
		joinCampus(memberToken, inviteCode);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campusId").value(campusId))
			.andExpect(jsonPath("$.data.name").value("13캠"))
			.andExpect(jsonPath("$.data.inviteCode").doesNotExist())
			.andExpect(jsonPath("$.data.myCampusRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.membershipStatus").value("ACTIVE"));

		String adminToken = signupAndLogin("detail-admin@example.com", UserRole.ADMIN);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campusId").value(campusId))
			.andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
			.andExpect(jsonPath("$.data.myCampusRole").doesNotExist())
			.andExpect(jsonPath("$.data.membershipStatus").doesNotExist());
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
			.andExpect(jsonPath("$.code").value("FORBIDDEN"))
			.andExpect(jsonPath("$.message").value("캠퍼스 조회 권한이 없습니다."));
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
