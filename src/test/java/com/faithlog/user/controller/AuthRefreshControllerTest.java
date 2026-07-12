package com.faithlog.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.user.service.port.AccessTokenBlacklistStore;
import com.faithlog.user.service.port.RefreshTokenStore;
import com.faithlog.user.support.InMemoryAccessTokenBlacklistStore;
import com.faithlog.user.support.InMemoryRefreshTokenStore;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
	private CoordinatedInMemoryRefreshTokenStore refreshTokenStore;

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
		refreshTokenStore.coordinateNextMatches(2);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			Callable<Integer> refreshRequest = () -> {
				ready.countDown();
				if (!start.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("동시 refresh 시작 대기 시간이 초과되었습니다.");
				}
				return mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
							{
							  "refreshToken": "%s"
							}
							""".formatted(tokens.refreshToken())))
					.andReturn()
					.getResponse()
					.getStatus();
			};

			Future<Integer> first = executor.submit(refreshRequest);
			Future<Integer> second = executor.submit(refreshRequest);
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(200, 401);
		}
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

	@TestConfiguration
	static class TestAuthTokenStoreConfig {

		@Bean
		CoordinatedInMemoryRefreshTokenStore refreshTokenStore() {
			return new CoordinatedInMemoryRefreshTokenStore();
		}

		@Bean
		@Primary
		RefreshTokenStore refreshTokenStorePort(CoordinatedInMemoryRefreshTokenStore refreshTokenStore) {
			return refreshTokenStore;
		}

		@Bean
		@Primary
		AccessTokenBlacklistStore accessTokenBlacklistStore() {
			return new InMemoryAccessTokenBlacklistStore();
		}
	}

	static class CoordinatedInMemoryRefreshTokenStore extends InMemoryRefreshTokenStore {

		private volatile CountDownLatch matchesBarrier;

		void coordinateNextMatches(int participants) {
			matchesBarrier = new CountDownLatch(participants);
		}

		@Override
		public boolean matchesCurrent(Long userId, String sessionId, String refreshJti) {
			boolean matches = super.matchesCurrent(userId, sessionId, refreshJti);
			CountDownLatch barrier = matchesBarrier;
			if (barrier != null) {
				barrier.countDown();
				try {
					if (!barrier.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("refresh JTI 비교 barrier 대기 시간이 초과되었습니다.");
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("refresh JTI 비교 barrier 대기가 중단되었습니다.", exception);
				} finally {
					matchesBarrier = null;
				}
			}
			return matches;
		}
	}
}
