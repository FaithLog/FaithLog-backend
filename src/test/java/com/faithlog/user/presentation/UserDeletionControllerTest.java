package com.faithlog.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import com.faithlog.user.application.port.AccessTokenBlacklistStore;
import com.faithlog.user.application.port.RefreshTokenStore;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import com.faithlog.user.support.InMemoryAccessTokenBlacklistStore;
import com.faithlog.user.support.InMemoryRefreshTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserDeletionControllerTest {

	private static final String PASSWORD = "1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private UserFcmTokenRepository userFcmTokenRepository;

	@Test
	void delete_me_soft_deletes_user_anonymizes_private_fields_and_revokes_access() throws Exception {
		String managerToken = signupAndLogin("delete-manager@example.com", UserRole.MANAGER).accessToken();
		String inviteCode = createCampus(managerToken, "탈퇴테스트캠").path("inviteCode").asText();
		TokenPair tokens = signupAndLogin("delete-me@example.com", UserRole.USER);
		User user = userRepository.findByEmail("delete-me@example.com").orElseThrow();
		long oldTokenVersion = user.tokenVersion();
		joinCampus(tokens.accessToken(), inviteCode);
		registerFcmToken(tokens.accessToken(), "delete-me-fcm-token", "delete-me-client");

		mockMvc.perform(delete("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "password": "1234",
					  "confirmText": "회원탈퇴"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."))
			.andExpect(jsonPath("$.data.deletedAt").exists());

		User deleted = userRepository.findById(user.id()).orElseThrow();
		assertThat(deleted.isActive()).isFalse();
		assertThat(deleted.deletedAt()).isNotNull();
		assertThat(deleted.email()).isEqualTo("deleted_user_%d@deleted.faithlog.local".formatted(user.id()));
		assertThat(deleted.name()).isEqualTo("탈퇴한 사용자");
		assertThat(deleted.tokenVersion()).isGreaterThan(oldTokenVersion);
		assertThat(userRepository.findByEmail("delete-me@example.com")).isEmpty();
		assertThat(campusMemberRepository.findByUserIdOrderByIdAsc(user.id()))
			.extracting(member -> member.status())
			.containsOnly(CampusMemberStatus.INACTIVE);
		assertThat(userFcmTokenRepository.findByUserIdAndClientInstanceIdAndIsActiveTrue(user.id(), "delete-me-client"))
			.isEmpty();

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(tokens.refreshToken())))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "재가입",
					  "email": "delete-me@example.com",
					  "password": "new-password"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.email").value("delete-me@example.com"));
	}

	@Test
	void delete_me_validates_password_confirm_text_and_deleted_status() throws Exception {
		TokenPair tokens = signupAndLogin("delete-invalid@example.com", UserRole.USER);

		mockMvc.perform(delete("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "password": "wrong",
					  "confirmText": "회원탈퇴"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("USER_DELETE_PASSWORD_MISMATCH"));

		mockMvc.perform(delete("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "password": "1234",
					  "confirmText": "삭제"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("USER_DELETE_CONFIRM_TEXT_INVALID"));

		mockMvc.perform(delete("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "password": "1234",
					  "confirmText": "회원탈퇴"
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "password": "1234",
					  "confirmText": "회원탈퇴"
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	private TokenPair signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "탈퇴테스트",
					  "email": "%s",
					  "password": "%s"
					}
					""".formatted(email, PASSWORD)))
			.andExpect(status().isCreated());

		User user = userRepository.findByEmail(email).orElseThrow();
		ReflectionTestUtils.setField(user, "role", role);
		userRepository.saveAndFlush(user);

		String loginBody = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "%s",
					  "password": "%s"
					}
					""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode login = objectMapper.readTree(loginBody).path("data");
		return new TokenPair(login.path("accessToken").asText(), login.path("refreshToken").asText());
	}

	private JsonNode createCampus(String accessToken, String name) throws Exception {
		String body = mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "region": "분당",
					  "description": "회원 탈퇴 테스트"
					}
					""".formatted(name)))
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

	private void registerFcmToken(String accessToken, String token, String clientInstanceId) throws Exception {
		mockMvc.perform(post("/api/v1/users/me/fcm-tokens")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "token": "%s",
					  "clientInstanceId": "%s",
					  "deviceType": "IOS",
					  "appVersion": "1.0.0"
					}
					""".formatted(token, clientInstanceId)))
			.andExpect(status().isOk());
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}

	@TestConfiguration
	static class TestUserDeletionPortConfig {

		@Bean
		@Primary
		RefreshTokenStore refreshTokenStore() {
			return new InMemoryRefreshTokenStore();
		}

		@Bean
		@Primary
		AccessTokenBlacklistStore accessTokenBlacklistStore() {
			return new InMemoryAccessTokenBlacklistStore();
		}
	}
}
