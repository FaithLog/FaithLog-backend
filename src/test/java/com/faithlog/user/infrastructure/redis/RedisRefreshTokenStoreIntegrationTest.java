package com.faithlog.user.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.user.service.port.RefreshTokenRotationResult;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "FAITHLOG_REDIS_INTEGRATION", matches = "true")
class RedisRefreshTokenStoreIntegrationTest {

	private static final Duration REFRESH_TTL = Duration.ofSeconds(1_209_600);
	private static final Duration REVOCATION_TTL = Duration.ofSeconds(1_209_660);

	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisRefreshTokenStore refreshTokenStore;
	private RedisSessionRevocationStore sessionRevocationStore;
	private Long userId;
	private String sessionId;

	@BeforeEach
	void setUp() {
		String host = System.getenv().getOrDefault("FAITHLOG_REDIS_HOST", "127.0.0.1");
		int port = Integer.parseInt(System.getenv().getOrDefault("FAITHLOG_REDIS_PORT", "6379"));
		connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		refreshTokenStore = new RedisRefreshTokenStore(redisTemplate);
		sessionRevocationStore = new RedisSessionRevocationStore(redisTemplate);
		userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
		sessionId = UUID.randomUUID().toString();
	}

	@AfterEach
	void tearDown() {
		if (redisTemplate != null) {
			redisTemplate.delete(List.of(refreshKey(), revokedKey()));
		}
		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
	}

	@Test
	void lua_rotation_allows_exactly_one_concurrent_winner_and_revoke_blocks_the_winner() throws Exception {
		String oldJti = UUID.randomUUID().toString();
		refreshTokenStore.saveCurrent(userId, sessionId, oldJti, REFRESH_TTL);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<RefreshTokenRotationResult> first = executor.submit(() -> rotateAfterBarrier(
				ready,
				start,
				oldJti,
				UUID.randomUUID().toString()
			));
			Future<RefreshTokenRotationResult> second = executor.submit(() -> rotateAfterBarrier(
				ready,
				start,
				oldJti,
				UUID.randomUUID().toString()
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(
					RefreshTokenRotationResult.ROTATED,
					RefreshTokenRotationResult.REJECTED
				);
		}

		assertThat(redisTemplate.hasKey(refreshKey())).isFalse();
		assertThat(sessionRevocationStore.isRevoked(userId, sessionId)).isTrue();
		assertThat(redisTemplate.getExpire(revokedKey(), TimeUnit.SECONDS)).isBetween(1_209_650L, 1_209_660L);
		assertThat(refreshTokenStore.rotate(
			userId,
			sessionId,
			oldJti,
			UUID.randomUUID().toString(),
			REFRESH_TTL
		)).isEqualTo(RefreshTokenRotationResult.REJECTED);
	}

	private RefreshTokenRotationResult rotateAfterBarrier(
		CountDownLatch ready,
		CountDownLatch start,
		String expectedJti,
		String newJti
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Redis CAS 동시 시작 대기 시간이 초과되었습니다.");
		}
		return refreshTokenStore.rotate(userId, sessionId, expectedJti, newJti, REFRESH_TTL);
	}

	private String refreshKey() {
		return "auth:refresh:" + userId + ":" + sessionId;
	}

	private String revokedKey() {
		return "auth:session:revoked:" + userId + ":" + sessionId;
	}
}
