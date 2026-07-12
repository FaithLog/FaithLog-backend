package com.faithlog.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.user.service.port.AccessTokenBlacklistStore;
import com.faithlog.user.service.port.CurrentDeviceFcmTokenDeactivationCommand;
import com.faithlog.user.service.port.CurrentDeviceFcmTokenDeactivationPort;
import com.faithlog.user.service.port.RefreshTokenStore;
import com.faithlog.user.support.InMemoryAccessTokenBlacklistStore;
import com.faithlog.user.support.InMemoryRefreshTokenStore;
import com.faithlog.user.support.RecordingCurrentDeviceFcmTokenDeactivationPort;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthLogoutControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private InMemoryRefreshTokenStore refreshTokenStore;

	@Autowired
	private InMemoryAccessTokenBlacklistStore accessTokenBlacklistStore;

	@Autowired
	private RecordingCurrentDeviceFcmTokenDeactivationPort fcmTokenDeactivationPort;

	@BeforeEach
	void setUp() {
		fcmTokenDeactivationPort.clear();
	}

	@Test
	void logout_blacklists_current_access_token_and_deletes_refresh_allowlist() throws Exception {
		TokenPair tokens = signupAndLogin("logout-blacklist@example.com");
		Claims accessClaims = jwtProvider.parseAccessToken(tokens.accessToken());
		Claims refreshClaims = jwtProvider.parseRefreshToken(tokens.refreshToken());

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("로그아웃되었습니다."));

		assertThat(accessTokenBlacklistStore.isBlacklisted(accessClaims.getId())).isTrue();
		assertThat(refreshTokenStore.hasSession(
			refreshClaims.get("userId", Long.class),
			refreshClaims.get("sessionId", String.class)
		)).isFalse();

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken()))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(tokens.refreshToken())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logout_succeeds_without_fcm_fields() throws Exception {
		TokenPair tokens = signupAndLogin("logout-no-fcm@example.com");

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("로그아웃되었습니다."));

		assertThat(fcmTokenDeactivationPort.calls()).isEmpty();
	}

	@Test
	void logout_calls_fcm_deactivation_port_for_current_device() throws Exception {
		TokenPair tokens = signupAndLogin("logout-fcm@example.com");
		Claims accessClaims = jwtProvider.parseAccessToken(tokens.accessToken());

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "clientInstanceId": "dummy-client-instance-id",
					  "fcmToken": "dummy-fcm-token"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		assertThat(fcmTokenDeactivationPort.calls()).containsExactly(new CurrentDeviceFcmTokenDeactivationCommand(
			accessClaims.get("userId", Long.class),
			"dummy-client-instance-id",
			"dummy-fcm-token"
		));
	}

	private TokenPair signupAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "로그아웃",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isCreated());

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

		JsonNode response = objectMapper.readTree(loginBody);
		return new TokenPair(
			response.path("data").path("accessToken").asText(),
			response.path("data").path("refreshToken").asText()
		);
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}

	@TestConfiguration
	static class TestAuthLogoutPortConfig {

		@Bean
		@Primary
		InMemoryRefreshTokenStore refreshTokenStore() {
			return new InMemoryRefreshTokenStore();
		}

		@Bean
		InMemoryAccessTokenBlacklistStore accessTokenBlacklistStore() {
			return new InMemoryAccessTokenBlacklistStore();
		}

		@Bean
		@Primary
		AccessTokenBlacklistStore accessTokenBlacklistStorePort(
			InMemoryAccessTokenBlacklistStore accessTokenBlacklistStore
		) {
			return accessTokenBlacklistStore;
		}

		@Bean
		RecordingCurrentDeviceFcmTokenDeactivationPort fcmTokenDeactivationPort() {
			return new RecordingCurrentDeviceFcmTokenDeactivationPort();
		}

		@Bean
		@Primary
		CurrentDeviceFcmTokenDeactivationPort fcmTokenDeactivationPortContract(
			RecordingCurrentDeviceFcmTokenDeactivationPort fcmTokenDeactivationPort
		) {
			return fcmTokenDeactivationPort;
		}
	}
}
