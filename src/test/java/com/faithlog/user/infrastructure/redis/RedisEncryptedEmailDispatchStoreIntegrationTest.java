package com.faithlog.user.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.infrastructure.email.AesGcmEmailDispatchCipher;
import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.port.EmailDispatchStore.EmailDispatchPayload;
import com.faithlog.user.service.port.EmailDispatchStore.EmailDispatchAcquisition;
import com.faithlog.user.service.port.EmailDispatchStore.AcquisitionStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
class RedisEncryptedEmailDispatchStoreIntegrationTest {

	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisEncryptedEmailDispatchStore store;
	private Set<String> keysBefore;

	@BeforeEach
	void setUp() {
		String host = System.getenv().getOrDefault("FAITHLOG_REDIS_HOST", "127.0.0.1");
		int port = Integer.parseInt(System.getenv().getOrDefault("FAITHLOG_REDIS_PORT", "6379"));
		connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		keysBefore = redisTemplate.keys("auth:email-dispatch:*");
		String key = Base64.getEncoder().encodeToString(
			"0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
		);
		store = new RedisEncryptedEmailDispatchStore(
			redisTemplate,
			new AesGcmEmailDispatchCipher(key, new ObjectMapper()),
			() -> "dispatch-" + UUID.randomUUID()
		);
	}

	@AfterEach
	void tearDown() {
		if (redisTemplate != null) {
			Set<String> keysAfter = redisTemplate.keys("auth:email-dispatch:*");
			Set<String> original = keysBefore == null ? Set.of() : keysBefore;
			if (keysAfter != null) {
				redisTemplate.delete(keysAfter.stream().filter(key -> !original.contains(key)).toList());
			}
		}
		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
	}

	@Test
	void stores_only_ciphertext_and_allows_one_lease_owner_at_a_time() throws Exception {
		String email = "private-" + UUID.randomUUID() + "@example.com";
		String code = "937251";
		EmailDispatchPayload payload = new EmailDispatchPayload(
			EmailVerificationPurpose.PASSWORD_RESET,
			email,
			code,
			300,
			true
		);
		String dispatchToken = store.create(payload, Duration.ofMinutes(5));
		assertRawRedisDoesNotContain(email, code, dispatchToken);

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<EmailDispatchAcquisition> first = executor.submit(
				() -> acquireAfterBarrier(ready, start, dispatchToken, "lease-a")
			);
			Future<EmailDispatchAcquisition> second = executor.submit(
				() -> acquireAfterBarrier(ready, start, dispatchToken, "lease-b")
			);
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			EmailDispatchAcquisition firstResult = first.get(5, TimeUnit.SECONDS);
			EmailDispatchAcquisition secondResult = second.get(5, TimeUnit.SECONDS);
			assertThat(List.of(firstResult.status(), secondResult.status()))
				.containsExactlyInAnyOrder(AcquisitionStatus.ACQUIRED, AcquisitionStatus.IN_PROGRESS);
			String winningLease = firstResult.status() == AcquisitionStatus.ACQUIRED ? "lease-a" : "lease-b";
			store.release(dispatchToken, winningLease);
		}

		assertThat(store.acquire(dispatchToken, "lease-final", Duration.ofSeconds(30)))
			.isEqualTo(EmailDispatchAcquisition.acquired(payload));
		assertThat(store.acknowledge(dispatchToken, "lease-final")).isTrue();
		assertThat(store.acquire(dispatchToken, "lease-after-ack", Duration.ofSeconds(30)))
			.isEqualTo(EmailDispatchAcquisition.missing());
	}

	private EmailDispatchAcquisition acquireAfterBarrier(
		CountDownLatch ready,
		CountDownLatch start,
		String dispatchToken,
		String leaseToken
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent dispatch acquisition timed out");
		}
		return store.acquire(dispatchToken, leaseToken, Duration.ofSeconds(30));
	}

	private void assertRawRedisDoesNotContain(String... secrets) {
		Set<String> keys = redisTemplate.keys("auth:email-dispatch:*");
		assertThat(keys).isNotNull().isNotEmpty();
		Set<String> original = keysBefore == null ? Set.of() : keysBefore;
		List<String> persisted = keys.stream()
			.filter(key -> !original.contains(key))
			.flatMap(key -> java.util.stream.Stream.of(key, redisTemplate.opsForValue().get(key)))
			.filter(java.util.Objects::nonNull)
			.toList();
		for (String secret : secrets) {
			assertThat(persisted).allSatisfy(value -> assertThat(value).doesNotContain(secret));
		}
	}
}
