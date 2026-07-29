package com.faithlog.user.infrastructure.redis;

import com.faithlog.user.infrastructure.email.AesGcmEmailDispatchCipher;
import com.faithlog.user.service.port.EmailDispatchQueueException;
import com.faithlog.user.service.port.EmailDispatchStore;
import com.faithlog.user.service.port.OneTimeTokenGenerator;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
	"${faithlog.auth.email-dispatch.cloud-tasks-enabled:false} or "
		+ "${faithlog.auth.email-dispatch.worker-enabled:false}"
)
public class RedisEncryptedEmailDispatchStore implements EmailDispatchStore {

	private static final int TOKEN_CREATION_ATTEMPTS = 3;
	private static final DefaultRedisScript<String> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
		local payload = redis.call('get', KEYS[1])
		if not payload then
		  return nil
		end
		local acquired = redis.call('set', KEYS[2], ARGV[1], 'PX', ARGV[2], 'NX')
		if not acquired then
		  return nil
		end
		return payload
		""", String.class);
	private static final DefaultRedisScript<Long> ACK_SCRIPT = new DefaultRedisScript<>("""
		if redis.call('get', KEYS[2]) ~= ARGV[1] then
		  return 0
		end
		redis.call('del', KEYS[1])
		redis.call('del', KEYS[2])
		return 1
		""", Long.class);
	private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
		if redis.call('get', KEYS[1]) ~= ARGV[1] then
		  return 0
		end
		return redis.call('del', KEYS[1])
		""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final AesGcmEmailDispatchCipher cipher;
	private final OneTimeTokenGenerator tokenGenerator;

	public RedisEncryptedEmailDispatchStore(
		StringRedisTemplate redisTemplate,
		AesGcmEmailDispatchCipher cipher,
		OneTimeTokenGenerator tokenGenerator
	) {
		this.redisTemplate = redisTemplate;
		this.cipher = cipher;
		this.tokenGenerator = tokenGenerator;
	}

	@Override
	public String create(EmailDispatchPayload payload, Duration ttl) {
		try {
			String ciphertext = cipher.encrypt(payload);
			for (int attempt = 0; attempt < TOKEN_CREATION_ATTEMPTS; attempt++) {
				String token = tokenGenerator.generate();
				Boolean created = redisTemplate.opsForValue().setIfAbsent(payloadKey(token), ciphertext, ttl);
				if (Boolean.TRUE.equals(created)) {
					return token;
				}
			}
			throw new EmailDispatchQueueException("Email dispatch token collision");
		} catch (EmailDispatchQueueException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new EmailDispatchQueueException("Email dispatch store is unavailable", exception);
		}
	}

	@Override
	public Optional<EmailDispatchPayload> acquire(String dispatchToken, String leaseToken, Duration leaseTtl) {
		try {
			String encrypted = redisTemplate.execute(
				ACQUIRE_SCRIPT,
				List.of(payloadKey(dispatchToken), leaseKey(dispatchToken)),
				leaseToken,
				String.valueOf(leaseTtl.toMillis())
			);
			return encrypted == null ? Optional.empty() : Optional.of(cipher.decrypt(encrypted));
		} catch (EmailDispatchQueueException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new EmailDispatchQueueException("Email dispatch store is unavailable", exception);
		}
	}

	@Override
	public boolean acknowledge(String dispatchToken, String leaseToken) {
		try {
			Long result = redisTemplate.execute(
				ACK_SCRIPT,
				List.of(payloadKey(dispatchToken), leaseKey(dispatchToken)),
				leaseToken
			);
			return Long.valueOf(1L).equals(result);
		} catch (RuntimeException exception) {
			throw new EmailDispatchQueueException("Email dispatch store is unavailable", exception);
		}
	}

	@Override
	public void release(String dispatchToken, String leaseToken) {
		try {
			redisTemplate.execute(RELEASE_SCRIPT, List.of(leaseKey(dispatchToken)), leaseToken);
		} catch (RuntimeException exception) {
			throw new EmailDispatchQueueException("Email dispatch store is unavailable", exception);
		}
	}

	@Override
	public void discard(String dispatchToken) {
		try {
			redisTemplate.delete(List.of(payloadKey(dispatchToken), leaseKey(dispatchToken)));
		} catch (RuntimeException exception) {
			throw new EmailDispatchQueueException("Email dispatch store is unavailable", exception);
		}
	}

	private String payloadKey(String token) {
		return "auth:email-dispatch:payload:" + cipher.fingerprint(token);
	}

	private String leaseKey(String token) {
		return "auth:email-dispatch:lease:" + cipher.fingerprint(token);
	}
}
