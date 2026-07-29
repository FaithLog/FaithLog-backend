package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.ConfirmEmailVerificationCommand;
import com.faithlog.user.service.command.RequestEmailVerificationCommand;
import com.faithlog.user.service.port.EmailSenderPort;
import com.faithlog.user.service.port.EmailVerificationStore;
import com.faithlog.user.service.port.EmailVerificationStore.ChallengeIssueResult;
import com.faithlog.user.service.port.EmailVerificationStore.ChallengeVerificationResult;
import com.faithlog.user.service.port.OneTimeTokenGenerator;
import com.faithlog.user.service.port.VerificationCodeGenerator;
import com.faithlog.user.service.result.EmailVerificationResult;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationCommandServiceTest {

	private static final EmailVerificationPolicy POLICY = new EmailVerificationPolicy(
		Duration.ofMinutes(5),
		Duration.ofSeconds(60),
		Duration.ofHours(1),
		Duration.ofMinutes(10),
		5,
		5
	);

	@Mock
	private UserRepository userRepository;

	@Mock
	private EmailVerificationStore verificationStore;

	@Mock
	private EmailSenderPort emailSenderPort;

	@Mock
	private VerificationCodeGenerator codeGenerator;

	@Mock
	private OneTimeTokenGenerator tokenGenerator;

	private EmailVerificationCommandService service;

	@BeforeEach
	void setUp() {
		service = new EmailVerificationCommandService(
			userRepository,
			verificationStore,
			emailSenderPort,
			codeGenerator,
			tokenGenerator,
			POLICY
		);
	}

	@Test
	void signup_request_normalizes_email_and_sends_a_six_digit_code() {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(codeGenerator.generate()).thenReturn("123456");
		when(verificationStore.issueChallenge(
			EmailVerificationPurpose.SIGNUP,
			"user@example.com",
			"123456",
			POLICY
		)).thenReturn(ChallengeIssueResult.ISSUED);

		var result = service.requestSignup(new RequestEmailVerificationCommand(" User@Example.COM "));

		assertThat(result.expiresInSeconds()).isEqualTo(300);
		assertThat(result.resendAvailableInSeconds()).isEqualTo(60);
		verify(emailSenderPort).sendVerificationCode(
			EmailVerificationPurpose.SIGNUP,
			"user@example.com",
			"123456",
			Duration.ofMinutes(5)
		);
	}

	@Test
	void signup_request_rejects_an_existing_email_before_issuing_a_challenge() {
		when(userRepository.existsByEmail("used@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.requestSignup(
			new RequestEmailVerificationCommand("used@example.com")
		))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS));

		verify(verificationStore, never()).issueChallenge(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void signup_confirmation_returns_a_short_lived_one_time_token() {
		when(tokenGenerator.generate()).thenReturn("signup-grant");
		when(verificationStore.confirmChallenge(
			EmailVerificationPurpose.SIGNUP,
			"user@example.com",
			"123456",
			"signup-grant",
			"user@example.com",
			POLICY
		)).thenReturn(ChallengeVerificationResult.VERIFIED);

		EmailVerificationResult result = service.confirmSignup(
			new ConfirmEmailVerificationCommand("USER@example.com", "123456")
		);

		assertThat(result.token()).isEqualTo("signup-grant");
		assertThat(result.expiresInSeconds()).isEqualTo(600);
	}

	@Test
	void password_reset_request_returns_the_same_result_for_unknown_email_without_sending_mail() {
		when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
		when(codeGenerator.generate()).thenReturn("654321");
		when(verificationStore.issueChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			"missing@example.com",
			"654321",
			POLICY
		)).thenReturn(ChallengeIssueResult.ISSUED);

		var result = service.requestPasswordReset(
			new RequestEmailVerificationCommand("missing@example.com")
		);

		assertThat(result.expiresInSeconds()).isEqualTo(300);
		assertThat(result.resendAvailableInSeconds()).isEqualTo(60);
		verify(emailSenderPort, never()).sendVerificationCode(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void password_reset_confirmation_binds_the_grant_to_the_active_user_id() {
		User user = User.create("사용자", "user@example.com", "encoded");
		org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 41L);
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(tokenGenerator.generate()).thenReturn("reset-grant");
		when(verificationStore.confirmChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			"user@example.com",
			"123456",
			"reset-grant",
			"41",
			POLICY
		)).thenReturn(ChallengeVerificationResult.VERIFIED);

		EmailVerificationResult result = service.confirmPasswordReset(
			new ConfirmEmailVerificationCommand("user@example.com", "123456")
		);

		assertThat(result.token()).isEqualTo("reset-grant");
		assertThat(result.expiresInSeconds()).isEqualTo(600);
	}

	@Test
	void password_reset_confirmation_discards_a_grant_for_an_unknown_email() {
		when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
		when(tokenGenerator.generate()).thenReturn("unusable-reset-grant");
		when(verificationStore.confirmChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			"missing@example.com",
			"123456",
			"unusable-reset-grant",
			"missing",
			POLICY
		)).thenReturn(ChallengeVerificationResult.VERIFIED);

		assertThatThrownBy(() -> service.confirmPasswordReset(
			new ConfirmEmailVerificationCommand("missing@example.com", "123456")
		))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_EMAIL_VERIFICATION_CODE_INVALID));

		verify(verificationStore).consumePasswordResetGrant("unusable-reset-grant");
	}
}
