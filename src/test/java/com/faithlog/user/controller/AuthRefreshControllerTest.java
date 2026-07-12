package com.faithlog.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.user.service.port.AccessTokenBlacklistStore;
import com.faithlog.user.support.InMemoryAccessTokenBlacklistStore;
import com.faithlog.user.support.InMemoryRefreshTokenStore;
import com.faithlog.user.service.port.RefreshTokenRotationResult;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRefreshControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private FaultInjectingInMemoryRefreshTokenStore refreshTokenStore;

	@BeforeEach
	void resetStoreFailures() {
		refreshTokenStore.failRotation = false;
		refreshTokenStore.failSessionCheck = false;
	}

	@Test
	void refresh_rotates_refresh_token_and_keeps_session_id() throws Exception {
		TokenPair tokens = signupAndLogin("refresh-rotate@example.com");
		Claims oldAccessClaims = jwtProvider.parseAccessToken(tokens.accessToken());
		Claims oldRefreshClaims = jwtProvider.parseRefreshToken(tokens.refreshToken());

		JsonNode refreshResponse = refresh(tokens.refreshToken(), status().isOk());
		String newAccessToken = refreshResponse.path("data").path("accessToken").asText();
		String newRefreshToken = refreshResponse.path("data").path("refreshToken").asText();
		Claims newAccessClaims = jwtProvider.parseAccessToken(newAccessToken);
		Claims newRefreshClaims = jwtProvider.parseRefreshToken(newRefreshToken);

		assertThat(newAccessToken).isNotEqualTo(tokens.accessToken());
		assertThat(newRefreshToken).isNotEqualTo(tokens.refreshToken());
		assertThat(newAccessClaims.get("sessionId", String.class)).isEqualTo(oldAccessClaims.get("sessionId", String.class));
		assertThat(newRefreshClaims.get("sessionId", String.class)).isEqualTo(oldRefreshClaims.get("sessionId", String.class));
		assertThat(newRefreshClaims.get("refreshJti", String.class))
			.isNotEqualTo(oldRefreshClaims.get("refreshJti", String.class));
		assertThat(refreshTokenStore.contains(
			newRefreshClaims.get("userId", Long.class),
			newRefreshClaims.get("sessionId", String.class),
			newRefreshClaims.get("refreshJti", String.class)
		)).isTrue();
	}

	@Test
	void refresh_rejects_reused_old_refresh_token_and_revokes_current_session() throws Exception {
		TokenPair tokens = signupAndLogin("refresh-reuse@example.com");

		JsonNode refreshResponse = refresh(tokens.refreshToken(), status().isOk());
		String rotatedRefreshToken = refreshResponse.path("data").path("refreshToken").asText();

		refreshAction(tokens.refreshToken(), status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		refreshAction(rotatedRefreshToken, status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	void concurrent_refresh_with_same_old_token_allows_exactly_one_rotation() throws Exception {
		TokenPair tokens = signupAndLogin("refresh-concurrent@example.com");
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			Callable<RefreshAttempt> refreshRequest = () -> {
				ready.countDown();
				if (!start.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("동시 refresh 시작 대기 시간이 초과되었습니다.");
				}
				var response = mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
							{
							  "refreshToken": "%s"
							}
							""".formatted(tokens.refreshToken())))
					.andReturn()
					.getResponse();
				return new RefreshAttempt(response.getStatus(), response.getContentAsString());
			};

			Future<RefreshAttempt> first = executor.submit(refreshRequest);
			Future<RefreshAttempt> second = executor.submit(refreshRequest);
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<RefreshAttempt> attempts = List.of(
				first.get(10, TimeUnit.SECONDS),
				second.get(10, TimeUnit.SECONDS)
			);
			assertThat(attempts).extracting(RefreshAttempt::status)
				.containsExactlyInAnyOrder(200, 401);

			RefreshAttempt winner = attempts.stream()
				.filter(attempt -> attempt.status() == 200)
				.findFirst()
				.orElseThrow();
			RefreshAttempt loser = attempts.stream()
				.filter(attempt -> attempt.status() == 401)
				.findFirst()
				.orElseThrow();
			JsonNode winnerBody = objectMapper.readTree(winner.body());
			assertThat(objectMapper.readTree(loser.body()).path("code").asText()).isEqualTo("AUTH_UNAUTHORIZED");

			mockMvc.perform(get("/api/v1/users/me")
					.header("Authorization", "Bearer " + winnerBody.path("data").path("accessToken").asText()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
			refreshAction(winnerBody.path("data").path("refreshToken").asText(), status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
		}
	}

	@Test
	void refresh_reuse_revokes_only_the_compromised_session() throws Exception {
		TokenPair compromisedSession = signupAndLogin("refresh-session-scope@example.com");
		TokenPair otherSession = login("refresh-session-scope@example.com");
		TokenPair otherUserSession = signupAndLogin("refresh-other-user@example.com");

		JsonNode rotated = refresh(compromisedSession.refreshToken(), status().isOk());
		refreshAction(compromisedSession.refreshToken(), status().isUnauthorized());

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + rotated.path("data").path("accessToken").asText()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + otherSession.accessToken()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + otherUserSession.accessToken()))
			.andExpect(status().isOk());

		refresh(otherSession.refreshToken(), status().isOk());
		refresh(otherUserSession.refreshToken(), status().isOk());
	}

	@Test
	void refresh_rejects_access_token_type_mismatch() throws Exception {
		TokenPair tokens = signupAndLogin("refresh-type-mismatch@example.com");

		refreshAction(tokens.accessToken(), status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	void refresh_fails_closed_when_rotation_store_is_unavailable() throws Exception {
		TokenPair tokens = signupAndLogin("refresh-redis-failure@example.com");
		refreshTokenStore.failRotation = true;

		assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(tokens.refreshToken()))))
			.hasRootCauseInstanceOf(IllegalStateException.class)
			.hasRootCauseMessage("test-only Redis rotation failure");
	}

	@Test
	void access_authentication_fails_closed_when_session_revocation_lookup_is_unavailable() throws Exception {
		TokenPair tokens = signupAndLogin("session-check-redis-failure@example.com");
		refreshTokenStore.failSessionCheck = true;

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	private TokenPair signupAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "리프레시",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isCreated());

		return login(email);
	}

	private TokenPair login(String email) throws Exception {
		String loginBody = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode response = objectMapper.readTree(loginBody);
		return new TokenPair(
			response.path("data").path("accessToken").asText(),
			response.path("data").path("refreshToken").asText()
		);
	}

	private JsonNode refresh(String refreshToken, org.springframework.test.web.servlet.ResultMatcher statusMatcher)
		throws Exception {
		String body = refreshAction(refreshToken, statusMatcher)
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body);
	}

	private org.springframework.test.web.servlet.ResultActions refreshAction(
		String refreshToken,
		org.springframework.test.web.servlet.ResultMatcher statusMatcher
	) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(refreshToken)))
			.andExpect(statusMatcher);
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}

	private record RefreshAttempt(int status, String body) {
	}

	@TestConfiguration
	static class TestAuthTokenStoreConfig {

		@Bean
		@Primary
		FaultInjectingInMemoryRefreshTokenStore refreshTokenStore() {
			return new FaultInjectingInMemoryRefreshTokenStore();
		}

		@Bean
		@Primary
		AccessTokenBlacklistStore accessTokenBlacklistStore() {
			return new InMemoryAccessTokenBlacklistStore();
		}
	}

	static class FaultInjectingInMemoryRefreshTokenStore extends InMemoryRefreshTokenStore {

		private volatile boolean failRotation;
		private volatile boolean failSessionCheck;

		@Override
		public RefreshTokenRotationResult rotate(
			Long userId,
			String sessionId,
			String expectedRefreshJti,
			String newRefreshJti,
			Duration ttl
		) {
			if (failRotation) {
				throw new IllegalStateException("test-only Redis rotation failure");
			}
			return super.rotate(userId, sessionId, expectedRefreshJti, newRefreshJti, ttl);
		}

		@Override
		public boolean isRevoked(Long userId, String sessionId) {
			if (failSessionCheck) {
				throw new IllegalStateException("test-only Redis session lookup failure");
			}
			return super.isRevoked(userId, sessionId);
		}
	}

}
