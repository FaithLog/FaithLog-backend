package com.faithlog.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserMeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Test
	void me_requires_bearer_token_and_returns_current_user() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "이승욱",
					  "email": "me@example.com",
					  "password": "1234"
					}
					"""))
			.andExpect(status().isCreated());

		String loginBody = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "me@example.com",
					  "password": "1234"
					}
					"""))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode response = objectMapper.readTree(loginBody);
		String accessToken = response.path("data").path("accessToken").asText();
		String refreshToken = response.path("data").path("refreshToken").asText();
		User user = userRepository.findByEmail("me@example.com").orElseThrow();

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
			.andExpect(jsonPath("$.data.id").value(user.id()))
			.andExpect(jsonPath("$.data.name").value("이승욱"))
			.andExpect(jsonPath("$.data.email").value("me@example.com"))
			.andExpect(jsonPath("$.data.role").value("USER"))
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andExpect(jsonPath("$.data.campusMemberships").isArray())
			.andExpect(jsonPath("$.timestamp").exists());

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + refreshToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void login_response_and_me_include_active_campus_memberships() throws Exception {
		String managerLoginBody = signupAndLogin("me-membership-manager@example.com", UserRole.MANAGER);
		String managerToken = objectMapper.readTree(managerLoginBody).path("data").path("accessToken").asText();
		JsonNode campus = createCampus(managerToken, "내정보캠");
		String memberLoginBody = signupAndLogin("me-membership-member@example.com", UserRole.USER);
		String memberToken = objectMapper.readTree(memberLoginBody).path("data").path("accessToken").asText();
		joinCampus(memberToken, campus.path("inviteCode").asText());

		String refreshedLoginBody = login("me-membership-member@example.com");
		String accessToken = objectMapper.readTree(refreshedLoginBody).path("data").path("accessToken").asText();

		JsonNode loginMembership = objectMapper.readTree(refreshedLoginBody)
			.path("data")
			.path("user")
			.path("campusMemberships")
			.path(0);
		assertThat(loginMembership.path("campusId").asLong()).isEqualTo(campus.path("campusId").asLong());
		assertThat(loginMembership.path("campusRole").asText()).isEqualTo("MEMBER");
		assertThat(loginMembership.path("status").asText()).isEqualTo("ACTIVE");

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campusMemberships.length()").value(1))
			.andExpect(jsonPath("$.data.campusMemberships[0].campusId").value(campus.path("campusId").asLong()))
			.andExpect(jsonPath("$.data.campusMemberships[0].campusRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.campusMemberships[0].status").value("ACTIVE"));
	}

	@Test
	void me_rejects_inactive_user_even_with_issued_access_token() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "비활성",
					  "email": "inactive@example.com",
					  "password": "1234"
					}
					"""))
			.andExpect(status().isCreated());

		String loginBody = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "inactive@example.com",
					  "password": "1234"
					}
					"""))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode response = objectMapper.readTree(loginBody);
		String accessToken = response.path("data").path("accessToken").asText();

		User user = userRepository.findByEmail("inactive@example.com").orElseThrow();
		ReflectionTestUtils.setField(user, "isActive", false);
		userRepository.saveAndFlush(user);

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.timestamp").exists());
	}

	private String signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "내정보테스트",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isCreated());

		User user = userRepository.findByEmail(email).orElseThrow();
		ReflectionTestUtils.setField(user, "role", role);
		userRepository.saveAndFlush(user);

		return login(email);
	}

	private String login(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
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
}
