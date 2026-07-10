package com.faithlog.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.notification.service.result.FcmTokenResult;
import com.faithlog.notification.service.FcmTokenService;
import com.faithlog.notification.service.command.RegisterFcmTokenCommand;
import com.faithlog.notification.domain.type.DeviceType;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.infrastructure.jpa.UserRepository;
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
class AuthLogoutFcmPersistenceTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private FcmTokenService fcmTokenService;

	@Autowired
	private UserFcmTokenRepository userFcmTokenRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void logout_deletes_current_device_active_fcm_token_row() throws Exception {
		TokenPair tokens = signupAndLogin("logout-delete-fcm@example.com");
		User user = userRepository.findByEmail("logout-delete-fcm@example.com").orElseThrow();
		FcmTokenResult registered = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"logout-delete-token",
			"logout-delete-client",
			DeviceType.IOS,
			"1.0.0"
		));

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "clientInstanceId": "logout-delete-client",
					  "fcmToken": "logout-delete-token"
					}
					"""))
			.andExpect(status().isOk());

		assertThat(userFcmTokenRepository.findById(registered.id())).isEmpty();
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
}
