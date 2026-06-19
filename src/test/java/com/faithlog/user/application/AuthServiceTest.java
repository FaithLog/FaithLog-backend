package com.faithlog.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.user.domain.User;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtProvider jwtProvider;

	@Test
	void signup_hashes_password_and_rejects_duplicate_email() {
		authService.signup(new SignupCommand("이승욱", "signup@example.com", "1234"));

		User user = userRepository.findByEmail("signup@example.com").orElseThrow();
		assertThat(user.passwordHash()).isNotEqualTo("1234");
		assertThat(passwordEncoder.matches("1234", user.passwordHash())).isTrue();
		assertThat(user.role().name()).isEqualTo("USER");

		assertThatThrownBy(() -> authService.signup(new SignupCommand("다른사용자", "signup@example.com", "1234")))
			.isInstanceOf(BusinessException.class)
			.hasMessage("이미 가입된 이메일입니다.")
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode().name())
				.isEqualTo("AUTH_EMAIL_ALREADY_EXISTS"));
	}

	@Test
	void login_returns_tokens_with_required_claims_and_updates_last_login_at() {
		authService.signup(new SignupCommand("이승욱", "login@example.com", "1234"));

		LoginResult response = authService.login(new LoginCommand("login@example.com", "1234"));

		Claims accessClaims = jwtProvider.parseAccessToken(response.accessToken());
		Claims refreshClaims = jwtProvider.parseRefreshToken(response.refreshToken());
		User user = userRepository.findByEmail("login@example.com").orElseThrow();

		assertThat(response.accessTokenExpiresIn()).isEqualTo(1800L);
		assertThat(response.refreshTokenExpiresIn()).isEqualTo(1209600L);
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(accessClaims.getId()).isNotBlank();
		assertThat(accessClaims.get("userId", Long.class)).isEqualTo(user.id());
		assertThat(accessClaims.get("role", String.class)).isEqualTo("USER");
		assertThat(accessClaims.get("sessionId", String.class)).isNotBlank();
		assertThat(accessClaims.get("tokenType", String.class)).isEqualTo("ACCESS");
		assertThat(refreshClaims.get("userId", Long.class)).isEqualTo(user.id());
		assertThat(refreshClaims.get("sessionId", String.class)).isEqualTo(accessClaims.get("sessionId", String.class));
		assertThat(refreshClaims.get("refreshJti", String.class)).isNotBlank();
		assertThat(refreshClaims.get("tokenType", String.class)).isEqualTo("REFRESH");
		assertThatThrownBy(() -> jwtProvider.parseAccessToken(response.refreshToken()))
			.hasMessage("Invalid token type");
		assertThatThrownBy(() -> jwtProvider.parseRefreshToken(response.accessToken()))
			.hasMessage("Invalid token type");
		assertThat(user.lastLoginAt()).isNotNull();
	}
}
