package com.faithlog.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
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
class FcmTokenControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserFcmTokenRepository userFcmTokenRepository;

	@Test
	void register_fcm_token_returns_upserted_token_response() throws Exception {
		String accessToken = signupAndLogin("fcm-http@example.com", UserRole.USER);

		String createdBody = mockMvc.perform(post("/api/v1/users/me/fcm-tokens")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "token": "http-fcm-token",
					  "clientInstanceId": "http-client",
					  "deviceType": "IOS",
					  "appVersion": "1.0.0"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.tokenId").isNumber())
			.andExpect(jsonPath("$.data.token").value("http-fcm-token"))
			.andExpect(jsonPath("$.data.deviceType").value("IOS"))
			.andExpect(jsonPath("$.data.clientInstanceId").value("http-client"))
			.andExpect(jsonPath("$.data.appVersion").value("1.0.0"))
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andExpect(jsonPath("$.data.lastSeenAt").isString())
			.andExpect(jsonPath("$.data.lastRefreshedAt").isString())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long tokenId = objectMapper.readTree(createdBody).path("data").path("tokenId").asLong();

		mockMvc.perform(post("/api/v1/users/me/fcm-tokens")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "token": "http-fcm-token",
					  "clientInstanceId": "http-client",
					  "deviceType": "IOS",
					  "appVersion": "1.1.0"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.tokenId").value(tokenId))
			.andExpect(jsonPath("$.data.appVersion").value("1.1.0"));
	}

	@Test
	void delete_fcm_token_requires_owner_and_returns_no_content_on_success() throws Exception {
		String ownerToken = signupAndLogin("fcm-http-owner@example.com", UserRole.USER);
		String otherToken = signupAndLogin("fcm-http-other@example.com", UserRole.USER);
		JsonNode registered = registerFcmToken(ownerToken, "owner-http-token", "owner-http-client");
		long tokenId = registered.path("tokenId").asLong();

		mockMvc.perform(delete("/api/v1/users/me/fcm-tokens/{tokenId}", tokenId)
				.header("Authorization", "Bearer " + otherToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOTIFICATION_FCM_TOKEN_NOT_FOUND"));

		mockMvc.perform(delete("/api/v1/users/me/fcm-tokens/{tokenId}", tokenId)
				.header("Authorization", "Bearer " + ownerToken))
			.andExpect(status().isNoContent());
	}

	@Test
	void logout_deletes_current_device_fcm_token_through_real_port() throws Exception {
		Tokens tokens = signupAndLoginTokens("fcm-http-logout@example.com", UserRole.USER);
		JsonNode registered = registerFcmToken(tokens.accessToken(), "logout-http-token", "logout-http-client");
		long tokenId = registered.path("tokenId").asLong();

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s",
					  "clientInstanceId": "logout-http-client",
					  "fcmToken": "logout-http-token"
					}
					""".formatted(tokens.refreshToken())))
			.andExpect(status().isOk());

		assertThat(userFcmTokenRepository.findById(tokenId)).isEmpty();
	}

	private JsonNode registerFcmToken(String accessToken, String token, String clientInstanceId) throws Exception {
		String body = mockMvc.perform(post("/api/v1/users/me/fcm-tokens")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "token": "%s",
					  "clientInstanceId": "%s",
					  "deviceType": "ANDROID",
					  "appVersion": "1.0.0"
					}
					""".formatted(token, clientInstanceId)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body).path("data");
	}

	private String signupAndLogin(String email, UserRole role) throws Exception {
		return signupAndLoginTokens(email, role).accessToken();
	}

	private Tokens signupAndLoginTokens(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "FCM테스트",
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
		JsonNode data = objectMapper.readTree(loginBody).path("data");
		return new Tokens(data.path("accessToken").asText(), data.path("refreshToken").asText());
	}

	private record Tokens(String accessToken, String refreshToken) {
	}
}
