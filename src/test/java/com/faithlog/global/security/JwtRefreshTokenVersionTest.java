package com.faithlog.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.user.domain.entity.User;
import io.jsonwebtoken.Claims;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtRefreshTokenVersionTest {

	@Test
	void refresh_token_contains_the_current_user_token_version() {
		JwtProvider jwtProvider = new JwtProvider(
			"test-secret",
			1800,
			1209600,
			Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC)
		);
		User user = User.create("사용자", "user@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", 7L);
		user.increaseTokenVersion();

		Claims claims = jwtProvider.parseRefreshToken(jwtProvider.issueTokens(user).refreshToken());

		assertThat(claims.get("tokenVersion", Number.class).longValue()).isEqualTo(1L);
	}
}
