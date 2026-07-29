package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.ConfirmEmailVerificationCommand;
import com.faithlog.user.service.command.LoginCommand;
import com.faithlog.user.service.command.RequestEmailVerificationCommand;
import com.faithlog.user.service.command.SignupCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EmailCanonicalizationIntegrationTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private EmailVerificationCommandService emailVerificationCommandService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void mixed_case_signup_can_login_with_the_same_original_email_spelling() {
		String localPart = "Mixed-" + UUID.randomUUID();
		String mixedCaseEmail = localPart + "@Example.COM";

		authService.signup(new SignupCommand("대소문자가입", mixedCaseEmail, "password"));

		authService.login(new LoginCommand(mixedCaseEmail, "password"));
	}

	@Test
	void legacy_mixed_case_account_can_complete_password_reset_with_its_email() {
		String localPart = "Legacy-" + UUID.randomUUID();
		String legacyEmail = localPart + "@Example.COM";
		userRepository.saveAndFlush(User.create(
			"레거시사용자",
			legacyEmail,
			passwordEncoder.encode("old-password")
		));

		emailVerificationCommandService.requestPasswordReset(
			new RequestEmailVerificationCommand(legacyEmail)
		);

		emailVerificationCommandService.confirmPasswordReset(
			new ConfirmEmailVerificationCommand(legacyEmail, "123456")
		);
	}

	@Test
	void legacy_mixed_case_account_blocks_a_logically_duplicate_lowercase_signup() {
		String localPart = "Duplicate-" + UUID.randomUUID();
		String legacyEmail = localPart + "@Example.COM";
		String canonicalEmail = legacyEmail.toLowerCase(java.util.Locale.ROOT);
		userRepository.saveAndFlush(User.create(
			"레거시사용자",
			legacyEmail,
			passwordEncoder.encode("old-password")
		));

		assertThatThrownBy(() -> authService.signup(
			new SignupCommand("중복사용자", canonicalEmail, "new-password")
		))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
				((BusinessException) exception).errorCode()
			).isEqualTo(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS));
	}
}
