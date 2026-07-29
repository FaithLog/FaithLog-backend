package com.faithlog.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationSecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@ParameterizedTest
	@MethodSource("publicEmailVerificationPaths")
	void email_verification_and_password_reset_paths_are_public(String path) throws Exception {
		mockMvc.perform(post(path)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

	private static List<String> publicEmailVerificationPaths() {
		return List.of(
			"/api/v1/auth/email-verifications/signup/request",
			"/api/v1/auth/email-verifications/signup/confirm",
			"/api/v1/auth/password-resets/request",
			"/api/v1/auth/password-resets/confirm",
			"/api/v1/auth/password-resets/complete"
		);
	}
}
