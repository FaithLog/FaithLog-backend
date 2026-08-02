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
import com.faithlog.user.service.command.ChangeMyPasswordCommand;
import com.faithlog.user.service.port.RefreshTokenStore;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthenticatedPasswordChangeCommandServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private RefreshTokenStore refreshTokenStore;

	private AuthenticatedPasswordChangeCommandService service;
	private User user;

	@BeforeEach
	void setUp() {
		service = new AuthenticatedPasswordChangeCommandService(
			userRepository,
			passwordEncoder,
			refreshTokenStore
		);
		user = User.create("사용자", "user@example.com", "old-hash");
		ReflectionTestUtils.setField(user, "id", 7L);
	}

	@Test
	void rejects_an_incorrect_current_password_without_mutation() {
		when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

		assertThatThrownBy(() -> service.changePassword(
			new ChangeMyPasswordCommand(7L, "wrong", "new-password")
		))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_CURRENT_PASSWORD_MISMATCH));

		assertThat(user.passwordHash()).isEqualTo("old-hash");
		assertThat(user.tokenVersion()).isZero();
		verify(passwordEncoder, never()).encode("new-password");
		verify(refreshTokenStore, never()).deleteAllSessions(7L);
	}

	@Test
	void rejects_the_same_new_password_without_revoking_sessions() {
		when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current", "old-hash")).thenReturn(true);
		when(passwordEncoder.matches("current", "old-hash")).thenReturn(true);

		assertThatThrownBy(() -> service.changePassword(
			new ChangeMyPasswordCommand(7L, "current", "current")
		))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_PASSWORD_CHANGE_SAME_PASSWORD));

		assertThat(user.passwordHash()).isEqualTo("old-hash");
		assertThat(user.tokenVersion()).isZero();
		verify(refreshTokenStore, never()).deleteAllSessions(7L);
	}

	@Test
	void changes_the_hash_increments_token_version_and_deletes_all_refresh_sessions() {
		when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current", "old-hash")).thenReturn(true);
		when(passwordEncoder.matches("new-password", "old-hash")).thenReturn(false);
		when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

		service.changePassword(new ChangeMyPasswordCommand(7L, "current", "new-password"));

		assertThat(user.passwordHash()).isEqualTo("new-hash");
		assertThat(user.tokenVersion()).isEqualTo(1L);
		verify(refreshTokenStore).deleteAllSessions(7L);
	}
}
