package com.faithlog.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.notification.domain.type.DeviceType;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import com.faithlog.notification.service.FcmTokenService;
import com.faithlog.notification.service.command.RegisterFcmTokenCommand;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.support.InMemoryEmailVerificationStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetSecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private FcmTokenService fcmTokenService;

	@Autowired
	private UserFcmTokenRepository userFcmTokenRepository;

	@Autowired
	private InMemoryEmailVerificationStore verificationStore;

	@Test
	void password_reset_revokes_old_access_refresh_and_password_but_keeps_fcm_token() throws Exception {
		String email = "reset-security-" + UUID.randomUUID() + "@example.com";
		signup(email, "old-password");
		TokenPair oldTokens = login(email, "old-password", status().isOk());
		User user = userRepository.findByEmail(email).orElseThrow();
		var fcm = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"reset-fcm-token",
			"reset-client",
			DeviceType.IOS,
			"1.0.0"
		));
		verificationStore.putPasswordResetGrant("reset-grant", user.id());

		mockMvc.perform(post("/api/v1/auth/password-resets/complete")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "resetToken": "reset-grant",
					  "newPassword": "new-password"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(jsonPath("$.refreshToken").doesNotExist());

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + oldTokens.accessToken()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(oldTokens.refreshToken())))
			.andExpect(status().isUnauthorized());
		login(email, "old-password", status().isUnauthorized());
		login(email, "new-password", status().isOk());

		assertThat(userFcmTokenRepository.findById(fcm.id())).get()
			.extracting(token -> token.isActive())
			.isEqualTo(true);
		assertThat(userRepository.findById(user.id()).orElseThrow().tokenVersion()).isEqualTo(1L);
		assertThat(verificationStore.resolvePasswordResetGrant("reset-grant")).isEmpty();
	}

	@Test
	void same_password_is_rejected_but_the_same_grant_can_retry_once_with_a_new_password() throws Exception {
		String email = "reset-same-" + UUID.randomUUID() + "@example.com";
		signup(email, "same-password");
		User user = userRepository.findByEmail(email).orElseThrow();
		verificationStore.putPasswordResetGrant("same-password-grant", user.id());

		mockMvc.perform(post("/api/v1/auth/password-resets/complete")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "resetToken": "same-password-grant",
					  "newPassword": "same-password"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_PASSWORD_RESET_SAME_PASSWORD"));

		mockMvc.perform(post("/api/v1/auth/password-resets/complete")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "resetToken": "same-password-grant",
					  "newPassword": "new-password"
					}
					"""))
			.andExpect(status().isOk());

		login(email, "new-password", status().isOk());
		assertThat(verificationStore.resolvePasswordResetGrant("same-password-grant")).isEmpty();
	}

	private void signup(String email, String password) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "비밀번호재설정",
					  "email": "%s",
					  "password": "%s"
					}
					""".formatted(email, password)))
			.andExpect(status().isCreated());
	}

	private TokenPair login(
		String email,
		String password,
		org.springframework.test.web.servlet.ResultMatcher statusMatcher
	) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "%s",
					  "password": "%s"
					}
					""".formatted(email, password)))
			.andExpect(statusMatcher)
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode response = objectMapper.readTree(body);
		return new TokenPair(
			response.path("data").path("accessToken").asText(),
			response.path("data").path("refreshToken").asText()
		);
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}
}
