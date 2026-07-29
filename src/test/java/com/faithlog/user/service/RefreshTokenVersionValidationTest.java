package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.RefreshCommand;
import com.faithlog.user.service.port.RefreshTokenStore;
import io.jsonwebtoken.Claims;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenVersionValidationTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtProvider jwtProvider;

	@Mock
	private RefreshTokenStore refreshTokenStore;

	@Mock
	private AuthTokenIssuanceSupport tokenIssuanceSupport;

	@Mock
	private Claims claims;

	private RefreshTokenRotationService service;
	private User user;

	@BeforeEach
	void setUp() {
		service = new RefreshTokenRotationService(
			userRepository,
			jwtProvider,
			refreshTokenStore,
			tokenIssuanceSupport,
			1209600
		);
		user = User.create("사용자", "user@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", 7L);
		user.increaseTokenVersion();
	}

	@Test
	void refresh_locks_the_user_and_rejects_a_token_issued_before_password_change() {
		when(jwtProvider.parseRefreshToken("old-refresh-token")).thenReturn(claims);
		when(claims.get("userId", Long.class)).thenReturn(7L);
		when(claims.get("sessionId", String.class)).thenReturn("session-id");
		when(claims.get("refreshJti", String.class)).thenReturn("refresh-jti");
		when(claims.get("tokenVersion", Number.class)).thenReturn(0L);
		when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.refresh(new RefreshCommand("old-refresh-token")))
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
				.isEqualTo(ErrorCode.AUTH_UNAUTHORIZED));

		verify(userRepository).findByIdForUpdate(7L);
		verify(refreshTokenStore).deleteSession(7L, "session-id");
		verify(refreshTokenStore, never()).rotate(
			org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		);
	}
}
