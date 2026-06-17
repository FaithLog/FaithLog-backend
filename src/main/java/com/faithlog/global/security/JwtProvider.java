package com.faithlog.global.security;

import com.faithlog.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

	private static final String TOKEN_TYPE_CLAIM = "tokenType";
	private static final String ACCESS_TOKEN_TYPE = "ACCESS";
	private static final String REFRESH_TOKEN_TYPE = "REFRESH";

	private final SecretKey secretKey;
	private final long accessTokenValiditySeconds;
	private final long refreshTokenValiditySeconds;
	private final Clock clock;

	@Autowired
	public JwtProvider(
		@Value("${faithlog.jwt.secret}") String secret,
		@Value("${faithlog.jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
		@Value("${faithlog.jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds
	) {
		this(secret, accessTokenValiditySeconds, refreshTokenValiditySeconds, Clock.systemUTC());
	}

	JwtProvider(
		String secret,
		long accessTokenValiditySeconds,
		long refreshTokenValiditySeconds,
		Clock clock
	) {
		this.secretKey = Keys.hmacShaKeyFor(sha256(secret));
		this.accessTokenValiditySeconds = accessTokenValiditySeconds;
		this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
		this.clock = clock;
	}

	public IssuedTokens issueTokens(User user) {
		String sessionId = UUID.randomUUID().toString();
		return issueTokens(user, sessionId);
	}

	public IssuedTokens issueTokens(User user, String sessionId) {
		String refreshJti = UUID.randomUUID().toString();
		String accessToken = issueAccessToken(user, sessionId);
		String refreshToken = issueRefreshToken(user, sessionId, refreshJti);
		return new IssuedTokens(
			accessToken,
			refreshToken,
			accessTokenValiditySeconds,
			refreshTokenValiditySeconds,
			user.id(),
			sessionId,
			refreshJti
		);
	}

	public Claims parseAccessToken(String token) {
		return parse(token, ACCESS_TOKEN_TYPE);
	}

	public Claims parseRefreshToken(String token) {
		return parse(token, REFRESH_TOKEN_TYPE);
	}

	private String issueAccessToken(User user, String sessionId) {
		Instant now = clock.instant();
		return Jwts.builder()
			.subject(String.valueOf(user.id()))
			.id(UUID.randomUUID().toString())
			.claim("userId", user.id())
			.claim("role", user.role().name())
			.claim("sessionId", sessionId)
			.claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plusSeconds(accessTokenValiditySeconds)))
			.signWith(secretKey)
			.compact();
	}

	private String issueRefreshToken(User user, String sessionId, String refreshJti) {
		Instant now = clock.instant();
		return Jwts.builder()
			.subject(String.valueOf(user.id()))
			.id(refreshJti)
			.claim("userId", user.id())
			.claim("sessionId", sessionId)
			.claim("refreshJti", refreshJti)
			.claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plusSeconds(refreshTokenValiditySeconds)))
			.signWith(secretKey)
			.compact();
	}

	private Claims parse(String token, String expectedTokenType) {
		Claims claims = Jwts.parser()
			.verifyWith(secretKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
		String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
		if (!expectedTokenType.equals(tokenType)) {
			throw new JwtException("Invalid token type");
		}
		return claims;
	}

	private byte[] sha256(String secret) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	public record IssuedTokens(
		String accessToken,
		String refreshToken,
		long accessTokenExpiresIn,
		long refreshTokenExpiresIn,
		Long userId,
		String sessionId,
		String refreshJti
	) {
	}
}
