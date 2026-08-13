package com.faithlog.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.user.domain.entity.User;
import io.jsonwebtoken.Claims;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtRefreshTokenVersionTest {

	@Test
	void refresh_token_contains_the_current_user_token_version() {
		JwtProvider jwtProvider = new JwtProvider(
			"test-secret",
			1800,
			1209600,
			Clock.systemUTC()
		);
		User user = User.create("사용자", "user@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", 7L);
		user.increaseTokenVersion();

		Claims claims = jwtProvider.parseRefreshToken(jwtProvider.issueTokens(user).refreshToken());

		assertThat(claims.get("tokenVersion", Number.class).longValue()).isEqualTo(1L);
	}
}
