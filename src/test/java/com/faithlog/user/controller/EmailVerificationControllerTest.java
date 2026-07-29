package com.faithlog.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.faithlog.global.security.AccessTokenBlacklistChecker;
import com.faithlog.global.security.AccessTokenVersionChecker;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.global.security.SessionRevocationChecker;
import com.faithlog.user.service.EmailVerificationCommandService;
import com.faithlog.user.service.PasswordResetCommandService;
import com.faithlog.user.service.command.CompletePasswordResetCommand;
import com.faithlog.user.service.result.EmailVerificationRequestResult;
import com.faithlog.user.service.result.EmailVerificationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmailVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmailVerificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EmailVerificationCommandService emailVerificationCommandService;

	@MockitoBean
	private PasswordResetCommandService passwordResetCommandService;

	@MockitoBean
	private JwtProvider jwtProvider;

	@MockitoBean
	private AccessTokenBlacklistChecker accessTokenBlacklistChecker;

	@MockitoBean
	private AccessTokenVersionChecker accessTokenVersionChecker;

	@MockitoBean
	private SessionRevocationChecker sessionRevocationChecker;

	@Test
	void requests_and_confirms_signup_email_verification() throws Exception {
		when(emailVerificationCommandService.requestSignup(any()))
			.thenReturn(new EmailVerificationRequestResult(300, 60));
		when(emailVerificationCommandService.confirmSignup(any()))
			.thenReturn(new EmailVerificationResult("signup-grant", 600));

		mockMvc.perform(post("/api/v1/auth/email-verifications/signup/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "user@example.com"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.expiresInSeconds").value(300))
			.andExpect(jsonPath("$.data.resendAvailableInSeconds").value(60));

		mockMvc.perform(post("/api/v1/auth/email-verifications/signup/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "user@example.com",
					  "code": "123456"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.emailVerificationToken").value("signup-grant"))
			.andExpect(jsonPath("$.data.expiresInSeconds").value(600));
	}

	@Test
	void password_reset_request_is_generic_and_confirmation_returns_an_opaque_grant() throws Exception {
		when(emailVerificationCommandService.requestPasswordReset(any()))
			.thenReturn(new EmailVerificationRequestResult(300, 60));
		when(emailVerificationCommandService.confirmPasswordReset(any()))
			.thenReturn(new EmailVerificationResult("reset-grant", 600));

		mockMvc.perform(post("/api/v1/auth/password-resets/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "unknown@example.com"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("가입된 이메일이라면 인증번호가 발송됩니다."))
			.andExpect(jsonPath("$.data.expiresInSeconds").value(300))
			.andExpect(jsonPath("$.data.resendAvailableInSeconds").value(60));

		mockMvc.perform(post("/api/v1/auth/password-resets/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "user@example.com",
					  "code": "654321"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.passwordResetToken").value("reset-grant"))
			.andExpect(jsonPath("$.data.expiresInSeconds").value(600));
	}

	@Test
	void completes_password_reset_without_automatic_login_tokens() throws Exception {
		mockMvc.perform(post("/api/v1/auth/password-resets/complete")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "resetToken": "reset-grant",
					  "newPassword": "new-password"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다. 다시 로그인해 주세요."))
			.andExpect(jsonPath("$.data").doesNotExist());

		verify(passwordResetCommandService).complete(
			new CompletePasswordResetCommand("reset-grant", "new-password")
		);
	}

	@Test
	void rejects_a_malformed_verification_code_before_the_service() throws Exception {
		mockMvc.perform(post("/api/v1/auth/password-resets/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "user@example.com",
					  "code": "12345A"
					}
					"""))
			.andExpect(status().isBadRequest());
	}
}
