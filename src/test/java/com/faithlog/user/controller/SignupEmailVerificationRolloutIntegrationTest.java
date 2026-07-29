package com.faithlog.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest(properties = "faithlog.auth.email-verification-required=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupEmailVerificationRolloutIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private InMemoryEmailVerificationStore verificationStore;

	@Test
	void required_mode_accepts_only_an_email_bound_one_time_token() throws Exception {
		String email = "required-" + UUID.randomUUID() + "@example.com";

		signup(email, null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_EMAIL_VERIFICATION_REQUIRED"));

		verificationStore.putSignupGrant("signup-grant", email);
		signup(email, "signup-grant")
			.andExpect(status().isCreated());

		assertThat(userRepository.findByEmail(email)).isPresent();
		assertThat(verificationStore.consumeSignupGrant(email, "signup-grant")).isFalse();

		String anotherEmail = "required-other-" + UUID.randomUUID() + "@example.com";
		signup(anotherEmail, "signup-grant")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_EMAIL_VERIFICATION_TOKEN_INVALID"));
		assertThat(userRepository.findByEmail(anotherEmail)).isEmpty();
	}

	private org.springframework.test.web.servlet.ResultActions signup(String email, String token) throws Exception {
		String tokenField = token == null ? "" : ",\n  \"emailVerificationToken\": \"" + token + "\"";
		return mockMvc.perform(post("/api/v1/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "name": "필수인증",
				  "email": "%s",
				  "password": "password"%s
				}
				""".formatted(email, tokenField)));
	}
}
