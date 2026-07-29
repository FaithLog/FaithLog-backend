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
import com.faithlog.user.service.command.CompletePasswordResetCommand;
import com.faithlog.user.service.command.QueueVerificationEmailCommand;
import com.faithlog.user.service.command.RequestEmailVerificationCommand;
import com.faithlog.user.service.policy.EmailVerificationPolicy;
import com.faithlog.user.service.port.EmailDispatchQueueException;
import com.faithlog.user.service.port.EmailDispatchQueuePort;
import com.faithlog.user.service.port.EmailVerificationStore;
import com.faithlog.user.service.port.EmailVerificationStoreException;
import com.faithlog.user.service.port.OneTimeTokenGenerator;
import com.faithlog.user.service.port.RefreshTokenStore;
import com.faithlog.user.service.port.VerificationCodeGenerator;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class EmailVerificationFailureBoundaryTest {

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
	private EmailDispatchQueuePort emailDispatchQueue;

	@Mock
	private VerificationCodeGenerator codeGenerator;

	@Mock
	private OneTimeTokenGenerator tokenGenerator;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private RefreshTokenStore refreshTokenStore;

	private EmailVerificationCommandService emailService;

	@BeforeEach
	void setUp() {
		emailService = new EmailVerificationCommandService(
			userRepository,
			verificationStore,
			emailDispatchQueue,
			codeGenerator,
			tokenGenerator,
			POLICY
		);
	}

	@Test
	void signup_mail_failure_cancels_the_challenge_and_returns_safe_service_unavailable() {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(codeGenerator.generate()).thenReturn("123456");
		when(verificationStore.issueChallenge(
			EmailVerificationPurpose.SIGNUP,
			"user@example.com",
			"123456",
			POLICY
		)).thenReturn(EmailVerificationStore.ChallengeIssueResult.ISSUED);
		org.mockito.Mockito.doThrow(new EmailDispatchQueueException("queue unavailable"))
			.when(emailDispatchQueue)
			.enqueue(new QueueVerificationEmailCommand(
				EmailVerificationPurpose.SIGNUP,
				"user@example.com",
				"123456",
				Duration.ofMinutes(5),
				true
			));

		assertThatThrownBy(() -> emailService.requestSignup(
			new RequestEmailVerificationCommand("user@example.com")
		))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_EMAIL_DELIVERY_UNAVAILABLE));

		verify(verificationStore).cancelChallenge(
			EmailVerificationPurpose.SIGNUP,
			"user@example.com",
			"123456"
		);
	}

	@Test
	void password_reset_mail_failure_keeps_the_same_challenge_state_as_an_unknown_account() {
		User user = User.create("사용자", "user@example.com", "hash");
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(codeGenerator.generate()).thenReturn("123456");
		when(verificationStore.issueChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			"user@example.com",
			"123456",
			POLICY
		)).thenReturn(EmailVerificationStore.ChallengeIssueResult.ISSUED);
		org.mockito.Mockito.doThrow(new EmailDispatchQueueException("queue unavailable"))
			.when(emailDispatchQueue)
			.enqueue(new QueueVerificationEmailCommand(
				EmailVerificationPurpose.PASSWORD_RESET,
				"user@example.com",
				"123456",
				Duration.ofMinutes(5),
				true
			));

		var result = emailService.requestPasswordReset(
			new RequestEmailVerificationCommand("user@example.com")
		);

		assertThat(result).isEqualTo(new com.faithlog.user.service.result.EmailVerificationRequestResult(300, 60));
		verify(verificationStore, never()).cancelChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			"user@example.com",
			"123456"
		);
	}

	@Test
	void password_reset_uses_the_same_queue_path_for_existing_and_missing_accounts() {
		User user = User.create("사용자", "known@example.com", "hash");
		when(userRepository.findByEmail("known@example.com")).thenReturn(Optional.of(user));
		when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
		when(codeGenerator.generate()).thenReturn("123456");
		when(verificationStore.issueChallenge(
			org.mockito.ArgumentMatchers.eq(EmailVerificationPurpose.PASSWORD_RESET),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.eq("123456"),
			org.mockito.ArgumentMatchers.eq(POLICY)
		)).thenReturn(EmailVerificationStore.ChallengeIssueResult.ISSUED);

		assertThat(emailService.requestPasswordReset(
			new RequestEmailVerificationCommand("known@example.com")
		)).isNotNull();
		assertThat(emailService.requestPasswordReset(
			new RequestEmailVerificationCommand("missing@example.com")
		)).isNotNull();

		verify(emailDispatchQueue).enqueue(new QueueVerificationEmailCommand(
			EmailVerificationPurpose.PASSWORD_RESET,
			"known@example.com",
			"123456",
			Duration.ofMinutes(5),
			true
		));
		verify(emailDispatchQueue).enqueue(new QueueVerificationEmailCommand(
			EmailVerificationPurpose.PASSWORD_RESET,
			"missing@example.com",
			"123456",
			Duration.ofMinutes(5),
			false
		));
	}

	@Test
	void Redis_failure_is_mapped_to_a_safe_service_unavailable_error() {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(codeGenerator.generate()).thenReturn("123456");
		when(verificationStore.issueChallenge(
			EmailVerificationPurpose.SIGNUP,
			"user@example.com",
			"123456",
			POLICY
		)).thenThrow(new EmailVerificationStoreException("Redis operation failed"));

		assertThatThrownBy(() -> emailService.requestSignup(
			new RequestEmailVerificationCommand("user@example.com")
		))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_EMAIL_VERIFICATION_UNAVAILABLE));

		verify(emailDispatchQueue, never()).enqueue(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void password_reset_grant_store_failure_is_mapped_before_any_password_or_session_change() {
		when(verificationStore.resolvePasswordResetGrant("reset-token"))
			.thenThrow(new EmailVerificationStoreException("Redis operation failed"));
		PasswordResetCommandService passwordResetService = new PasswordResetCommandService(
			userRepository,
			verificationStore,
			passwordEncoder,
			refreshTokenStore
		);

		assertThatThrownBy(() -> passwordResetService.complete(
			new CompletePasswordResetCommand("reset-token", "new-password")
		))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_EMAIL_VERIFICATION_UNAVAILABLE));

		verify(userRepository, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.anyLong());
		verify(refreshTokenStore, never()).deleteAllSessions(org.mockito.ArgumentMatchers.anyLong());
	}
}
