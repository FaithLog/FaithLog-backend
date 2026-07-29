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
import com.faithlog.user.service.port.EmailVerificationStore;
import com.faithlog.user.service.port.RefreshTokenStore;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetCommandServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private EmailVerificationStore verificationStore;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private RefreshTokenStore refreshTokenStore;

	private PasswordResetCommandService service;
	private User user;

	@BeforeEach
	void setUp() {
		service = new PasswordResetCommandService(
			userRepository,
			verificationStore,
			passwordEncoder,
			refreshTokenStore
		);
		user = User.create("사용자", "user@example.com", "old-hash");
		ReflectionTestUtils.setField(user, "id", 7L);
	}

	@Test
	void complete_rejects_the_current_password_with_a_dedicated_error() {
		when(verificationStore.resolvePasswordResetGrant("reset-token")).thenReturn(OptionalLong.of(7L));
		when(userRepository.findByIdForUpdate(7L)).thenReturn(java.util.Optional.of(user));
		when(passwordEncoder.matches("same-password", "old-hash")).thenReturn(true);

		assertThatThrownBy(() -> service.complete(
			new CompletePasswordResetCommand("reset-token", "same-password")
		))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_PASSWORD_RESET_SAME_PASSWORD));

		verify(refreshTokenStore, never()).deleteAllSessions(7L);
	}

	@Test
	void complete_changes_the_hash_increments_token_version_and_deletes_all_refresh_sessions() {
		when(verificationStore.resolvePasswordResetGrant("reset-token")).thenReturn(OptionalLong.of(7L));
		when(verificationStore.consumePasswordResetGrant("reset-token", 7L)).thenReturn(true);
		when(userRepository.findByIdForUpdate(7L)).thenReturn(java.util.Optional.of(user));
		when(passwordEncoder.matches("new-password", "old-hash")).thenReturn(false);
		when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

		service.complete(new CompletePasswordResetCommand("reset-token", "new-password"));

		assertThat(user.passwordHash()).isEqualTo("new-hash");
		assertThat(user.tokenVersion()).isEqualTo(1L);
		verify(refreshTokenStore).deleteAllSessions(7L);
		verify(verificationStore).consumePasswordResetGrant("reset-token", 7L);
	}
}
