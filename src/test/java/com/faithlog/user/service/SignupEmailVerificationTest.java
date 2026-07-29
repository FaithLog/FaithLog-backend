package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.SignupCommand;
import com.faithlog.user.service.port.EmailVerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SignupEmailVerificationTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private EmailVerificationStore verificationStore;

	private SignupCommandService requiredService;
	private SignupCommandService compatibleService;

	@BeforeEach
	void setUp() {
		requiredService = new SignupCommandService(userRepository, passwordEncoder, verificationStore, true);
		compatibleService = new SignupCommandService(userRepository, passwordEncoder, verificationStore, false);
	}

	@Test
	void required_mode_rejects_signup_without_a_verification_token() {
		assertThatThrownBy(() -> requiredService.signup(
			new SignupCommand("사용자", "user@example.com", "password", null)
		))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_EMAIL_VERIFICATION_REQUIRED));

		verify(userRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void compatible_mode_allows_an_absent_token_but_consumes_every_supplied_token() {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(verificationStore.consumeSignupGrant("user@example.com", "grant-token")).thenReturn(true);
		when(passwordEncoder.encode("password")).thenReturn("encoded");
		when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

		compatibleService.signup(new SignupCommand(
			"사용자",
			" USER@example.com ",
			"password",
			"grant-token"
		));

		verify(verificationStore).consumeSignupGrant("user@example.com", "grant-token");
		verify(userRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(user ->
			user.email().equals("USER@example.com")
		));
	}

	@Test
	void compatible_mode_rejects_an_invalid_supplied_token() {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(verificationStore.consumeSignupGrant("user@example.com", "invalid-token")).thenReturn(false);

		assertThatThrownBy(() -> compatibleService.signup(new SignupCommand(
			"사용자",
			"user@example.com",
			"password",
			"invalid-token"
		)))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_EMAIL_VERIFICATION_TOKEN_INVALID));

		verify(userRepository).saveAndFlush(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void database_unique_constraints_are_flushed_before_the_one_time_grant_is_consumed() {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(passwordEncoder.encode("password")).thenReturn("encoded");
		when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(verificationStore.consumeSignupGrant("user@example.com", "grant-token")).thenReturn(true);

		compatibleService.signup(new SignupCommand(
			"사용자",
			"user@example.com",
			"password",
			"grant-token"
		));

		var ordered = inOrder(userRepository, verificationStore);
		ordered.verify(userRepository).saveAndFlush(any());
		ordered.verify(verificationStore).consumeSignupGrant("user@example.com", "grant-token");
	}

	@Test
	void duplicate_email_constraint_race_returns_the_existing_error_without_consuming_the_grant() {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(passwordEncoder.encode("password")).thenReturn("encoded");
		when(userRepository.saveAndFlush(any())).thenThrow(
			new org.springframework.dao.DataIntegrityViolationException("duplicate email")
		);

		assertThatThrownBy(() -> compatibleService.signup(new SignupCommand(
			"사용자",
			"user@example.com",
			"password",
			"grant-token"
		)))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS));

		verify(verificationStore, never()).consumeSignupGrant("user@example.com", "grant-token");
	}
}
