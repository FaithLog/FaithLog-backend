package com.faithlog.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserPasswordChangeControllerTest {

	private static final String CURRENT_PASSWORD = "12345678";
	private static final String NEW_PASSWORD = "87654321";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void changes_password_revokes_all_tokens_and_requires_login_with_the_new_password() throws Exception {
		TokenPair tokens = signupAndLogin("password-change-success@example.com");
		User before = userRepository.findByEmail("password-change-success@example.com").orElseThrow();

		mockMvc.perform(patch("/api/v1/users/me/password")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(CURRENT_PASSWORD, NEW_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다. 다시 로그인해 주세요."))
			.andExpect(jsonPath("$.data").doesNotExist());

		User after = userRepository.findById(before.id()).orElseThrow();
		assertThat(passwordEncoder.matches(NEW_PASSWORD, after.passwordHash())).isTrue();
		assertThat(after.tokenVersion()).isEqualTo(before.tokenVersion() + 1);
		assertThat(after.name()).isEqualTo(before.name());
		assertThat(after.email()).isEqualTo(before.email());
		assertThat(after.role()).isEqualTo(before.role());
		assertThat(after.isActive()).isEqualTo(before.isActive());

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"%s\"}".formatted(tokens.refreshToken())))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		login("password-change-success@example.com", CURRENT_PASSWORD)
			.andExpect(status().isUnauthorized());
		login("password-change-success@example.com", NEW_PASSWORD)
			.andExpect(status().isOk());
	}

	@Test
	void rejects_an_incorrect_current_password_and_preserves_password_and_tokens() throws Exception {
		TokenPair tokens = signupAndLogin("password-change-mismatch@example.com");
		User before = userRepository.findByEmail("password-change-mismatch@example.com").orElseThrow();

		mockMvc.perform(patch("/api/v1/users/me/password")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody("wrong-password", NEW_PASSWORD)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_CURRENT_PASSWORD_MISMATCH"));

		User after = userRepository.findById(before.id()).orElseThrow();
		assertThat(after.passwordHash()).isEqualTo(before.passwordHash());
		assertThat(after.tokenVersion()).isEqualTo(before.tokenVersion());

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"%s\"}".formatted(tokens.refreshToken())))
			.andExpect(status().isOk());
	}

	@Test
	void rejects_reusing_the_current_password_without_revoking_the_session() throws Exception {
		TokenPair tokens = signupAndLogin("password-change-same@example.com");
		User before = userRepository.findByEmail("password-change-same@example.com").orElseThrow();

		mockMvc.perform(patch("/api/v1/users/me/password")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(CURRENT_PASSWORD, CURRENT_PASSWORD)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_PASSWORD_CHANGE_SAME_PASSWORD"));

		User after = userRepository.findById(before.id()).orElseThrow();
		assertThat(after.passwordHash()).isEqualTo(before.passwordHash());
		assertThat(after.tokenVersion()).isEqualTo(before.tokenVersion());
		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken()))
			.andExpect(status().isOk());
	}

	@Test
	void rejects_blank_fields_before_changing_the_password() throws Exception {
		TokenPair tokens = signupAndLogin("password-change-validation@example.com");

		mockMvc.perform(patch("/api/v1/users/me/password")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody("", NEW_PASSWORD)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("GLOBAL_VALIDATION_FAILED"));

		mockMvc.perform(patch("/api/v1/users/me/password")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(CURRENT_PASSWORD, "   ")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("GLOBAL_VALIDATION_FAILED"));
	}

	@Test
	void rejects_missing_access_token_and_refresh_token_bearer() throws Exception {
		TokenPair tokens = signupAndLogin("password-change-auth@example.com");
		String body = requestBody(CURRENT_PASSWORD, NEW_PASSWORD);

		mockMvc.perform(patch("/api/v1/users/me/password")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		mockMvc.perform(patch("/api/v1/users/me/password")
				.header("Authorization", "Bearer " + tokens.refreshToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	private TokenPair signupAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "비밀번호 변경 사용자",
					  "email": "%s",
					  "password": "%s"
					}
					""".formatted(email, CURRENT_PASSWORD)))
			.andExpect(status().isCreated());

		String response = login(email, CURRENT_PASSWORD)
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode data = objectMapper.readTree(response).path("data");
		return new TokenPair(data.path("accessToken").asText(), data.path("refreshToken").asText());
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "email": "%s",
				  "password": "%s"
				}
				""".formatted(email, password)));
	}

	private String requestBody(String currentPassword, String newPassword) {
		return """
			{
			  "currentPassword": "%s",
			  "newPassword": "%s"
			}
			""".formatted(currentPassword, newPassword);
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}
}
