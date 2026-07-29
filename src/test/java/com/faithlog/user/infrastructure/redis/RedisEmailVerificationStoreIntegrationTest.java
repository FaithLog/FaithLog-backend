package com.faithlog.user.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.policy.EmailVerificationPolicy;
import com.faithlog.user.service.port.EmailVerificationStore.ChallengeIssueResult;
import com.faithlog.user.service.port.EmailVerificationStore.ChallengeVerificationResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "FAITHLOG_REDIS_INTEGRATION", matches = "true")
class RedisEmailVerificationStoreIntegrationTest {

	private static final EmailVerificationPolicy POLICY = new EmailVerificationPolicy(
		Duration.ofMinutes(5),
		Duration.ofSeconds(60),
		Duration.ofHours(1),
		Duration.ofMinutes(10),
		5,
		5
	);

	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisEmailVerificationStore store;
	private Set<String> keysBefore;
	private String email;

	@BeforeEach
	void setUp() {
		String host = System.getenv().getOrDefault("FAITHLOG_REDIS_HOST", "127.0.0.1");
		int port = Integer.parseInt(System.getenv().getOrDefault("FAITHLOG_REDIS_PORT", "6379"));
		connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		keysBefore = redisTemplate.keys("auth:email-verification:*");
		store = new RedisEmailVerificationStore(
			redisTemplate,
			new HmacVerificationSecretHasher(java.util.Base64.getEncoder().encodeToString(
				"0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8)
			))
		);
		email = "issue-224-" + UUID.randomUUID() + "@example.com";
	}

	@AfterEach
	void tearDown() {
		if (redisTemplate != null) {
			Set<String> keysAfter = redisTemplate.keys("auth:email-verification:*");
			if (keysAfter != null) {
				Set<String> original = keysBefore == null ? Set.of() : keysBefore;
				redisTemplate.delete(keysAfter.stream().filter(key -> !original.contains(key)).toList());
			}
		}
		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
	}

	@Test
	void challenge_and_grant_never_store_raw_secrets_and_signup_grant_has_one_concurrent_winner() throws Exception {
		String code = "123456";
		String grant = "opaque-signup-grant-" + UUID.randomUUID();
		assertThat(store.issueChallenge(EmailVerificationPurpose.SIGNUP, email, code, POLICY))
			.isEqualTo(ChallengeIssueResult.ISSUED);
		assertThat(store.confirmChallenge(
			EmailVerificationPurpose.SIGNUP,
			email,
			code,
			grant,
			email,
			POLICY
		)).isEqualTo(ChallengeVerificationResult.VERIFIED);

		assertNoRawSecrets(email, code, grant);
		assertThat(store.consumeSignupGrant("other-" + email, grant)).isFalse();

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<Boolean> first = executor.submit(() -> consumeAfterBarrier(ready, start, grant));
			Future<Boolean> second = executor.submit(() -> consumeAfterBarrier(ready, start, grant));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(true, false);
		}
	}

	@Test
	void fifth_wrong_code_blocks_the_challenge_and_purpose_or_email_mismatch_never_consumes_a_grant() {
		String code = "654321";
		String grant = "opaque-reset-grant-" + UUID.randomUUID();
		assertThat(store.issueChallenge(EmailVerificationPurpose.PASSWORD_RESET, email, code, POLICY))
			.isEqualTo(ChallengeIssueResult.ISSUED);

		for (int attempt = 1; attempt < POLICY.maxAttempts(); attempt++) {
			assertThat(store.confirmChallenge(
				EmailVerificationPurpose.PASSWORD_RESET,
				email,
				"000000",
				grant,
				"41",
				POLICY
			)).isEqualTo(ChallengeVerificationResult.INVALID);
		}
		assertThat(store.confirmChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			email,
			"000000",
			grant,
			"41",
			POLICY
		)).isEqualTo(ChallengeVerificationResult.ATTEMPTS_EXCEEDED);
		assertThat(store.confirmChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			email,
			code,
			grant,
			"41",
			POLICY
		)).isEqualTo(ChallengeVerificationResult.ATTEMPTS_EXCEEDED);
		assertThat(store.consumeSignupGrant(email, grant)).isFalse();
		assertThat(store.resolvePasswordResetGrant(grant)).isEmpty();
	}

	@Test
	void cooldown_rate_limit_and_expiration_are_enforced_by_redis_ttl() throws Exception {
		EmailVerificationPolicy shortPolicy = new EmailVerificationPolicy(
			Duration.ofMillis(150),
			Duration.ofMillis(100),
			Duration.ofSeconds(5),
			Duration.ofMillis(150),
			5,
			2
		);

		assertThat(store.issueChallenge(
			EmailVerificationPurpose.SIGNUP,
			email,
			"123456",
			shortPolicy
		)).isEqualTo(ChallengeIssueResult.ISSUED);
		assertThat(store.issueChallenge(
			EmailVerificationPurpose.SIGNUP,
			email,
			"123456",
			shortPolicy
		)).isEqualTo(ChallengeIssueResult.COOLDOWN);

		Thread.sleep(200);
		assertThat(store.confirmChallenge(
			EmailVerificationPurpose.SIGNUP,
			email,
			"123456",
			"expired-challenge-grant",
			email,
			shortPolicy
		)).isEqualTo(ChallengeVerificationResult.EXPIRED);
		assertThat(store.issueChallenge(
			EmailVerificationPurpose.SIGNUP,
			email,
			"123456",
			shortPolicy
		)).isEqualTo(ChallengeIssueResult.ISSUED);

		Thread.sleep(120);
		assertThat(store.issueChallenge(
			EmailVerificationPurpose.SIGNUP,
			email,
			"123456",
			shortPolicy
		)).isEqualTo(ChallengeIssueResult.RATE_LIMITED);

		String grantEmail = "grant-" + email;
		assertThat(store.issueChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			grantEmail,
			"654321",
			shortPolicy
		)).isEqualTo(ChallengeIssueResult.ISSUED);
		assertThat(store.confirmChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			grantEmail,
			"654321",
			"expiring-reset-grant",
			"41",
			shortPolicy
		)).isEqualTo(ChallengeVerificationResult.VERIFIED);
		Thread.sleep(200);
		assertThat(store.resolvePasswordResetGrant("expiring-reset-grant")).isEmpty();
	}

	private boolean consumeAfterBarrier(CountDownLatch ready, CountDownLatch start, String grant)
		throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent grant consumption timed out");
		}
		return store.consumeSignupGrant(email, grant);
	}

	private void assertNoRawSecrets(String... secrets) {
		Set<String> keys = redisTemplate.keys("auth:email-verification:*");
		assertThat(keys).isNotNull().isNotEmpty();
		Set<String> original = keysBefore == null ? Set.of() : keysBefore;
		List<String> createdKeys = keys.stream().filter(key -> !original.contains(key)).toList();
		List<String> persisted = new ArrayList<>(createdKeys);
		for (String key : createdKeys) {
			DataType type = redisTemplate.type(key);
			if (type == DataType.STRING) {
				String value = redisTemplate.opsForValue().get(key);
				if (value != null) {
					persisted.add(value);
				}
			} else if (type == DataType.HASH) {
				persisted.addAll(redisTemplate.opsForHash().entries(key).entrySet().stream()
					.map(entry -> entry.getKey() + "=" + entry.getValue())
					.toList());
			} else {
				throw new AssertionError("Unexpected Redis data type: " + type);
			}
		}
		for (String secret : secrets) {
			assertThat(persisted).allSatisfy(item -> assertThat(item).doesNotContain(secret));
		}
	}
}
