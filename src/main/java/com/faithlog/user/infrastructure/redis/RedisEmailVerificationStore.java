package com.faithlog.user.infrastructure.redis;

import com.faithlog.user.service.EmailVerificationPolicy;
import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.port.EmailVerificationStore;
import com.faithlog.user.service.port.EmailVerificationStoreException;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisEmailVerificationStore implements EmailVerificationStore {

	private static final String CONSTANT_TIME_EQUALS = """
		local function constant_time_equals(first, second)
		  if (not first) or (not second) or (#first ~= #second) then
		    return false
		  end
		  local different = 0
		  for index = 1, #first do
		    if string.byte(first, index) ~= string.byte(second, index) then
		      different = 1
		    end
		  end
		  return different == 0
		end
		""";

	private static final DefaultRedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>("""
		if redis.call('exists', KEYS[2]) == 1 then
		  return 1
		end
		local request_count = redis.call('incr', KEYS[3])
		if request_count == 1 then
		  redis.call('pexpire', KEYS[3], ARGV[4])
		end
		if request_count > tonumber(ARGV[5]) then
		  return 2
		end
		redis.call('hset', KEYS[1], 'code', ARGV[1], 'attempts', '0', 'blocked', '0')
		redis.call('pexpire', KEYS[1], ARGV[2])
		redis.call('psetex', KEYS[2], ARGV[3], '1')
		return 0
		""", Long.class);

	private static final DefaultRedisScript<Long> CANCEL_SCRIPT = new DefaultRedisScript<>(
		CONSTANT_TIME_EQUALS + """
		local stored = redis.call('hget', KEYS[1], 'code')
		if constant_time_equals(stored, ARGV[1]) then
		  redis.call('del', KEYS[1])
		  redis.call('del', KEYS[2])
		  return 1
		end
		return 0
		""",
		Long.class
	);

	private static final DefaultRedisScript<Long> CONFIRM_SCRIPT = new DefaultRedisScript<>(
		CONSTANT_TIME_EQUALS + """
		if redis.call('exists', KEYS[1]) == 0 then
		  return 2
		end
		if redis.call('hget', KEYS[1], 'blocked') == '1' then
		  return 3
		end
		local stored = redis.call('hget', KEYS[1], 'code')
		if not constant_time_equals(stored, ARGV[1]) then
		  local attempts = redis.call('hincrby', KEYS[1], 'attempts', 1)
		  if attempts >= tonumber(ARGV[4]) then
		    redis.call('hset', KEYS[1], 'blocked', '1')
		    return 3
		  end
		  return 1
		end
		local stored_grant = redis.call('set', KEYS[2], ARGV[2], 'PX', ARGV[3], 'NX')
		if not stored_grant then
		  return 4
		end
		redis.call('del', KEYS[1])
		return 0
		""",
		Long.class
	);

	private static final DefaultRedisScript<Long> CONSUME_SIGNUP_SCRIPT = new DefaultRedisScript<>(
		CONSTANT_TIME_EQUALS + """
		local subject = redis.call('get', KEYS[1])
		if not constant_time_equals(subject, ARGV[1]) then
		  return 0
		end
		redis.call('del', KEYS[1])
		return 1
		""",
		Long.class
	);

	private static final DefaultRedisScript<String> CONSUME_RESET_SCRIPT = new DefaultRedisScript<>("""
		local subject = redis.call('get', KEYS[1])
		if not subject then
		  return nil
		end
		redis.call('del', KEYS[1])
		return subject
		""", String.class);

	private final StringRedisTemplate redisTemplate;
	private final HmacVerificationSecretHasher hasher;

	public RedisEmailVerificationStore(
		StringRedisTemplate redisTemplate,
		HmacVerificationSecretHasher hasher
	) {
		this.redisTemplate = redisTemplate;
		this.hasher = hasher;
	}

	@Override
	public ChallengeIssueResult issueChallenge(
		EmailVerificationPurpose purpose,
		String email,
		String code,
		EmailVerificationPolicy policy
	) {
		try {
			String emailFingerprint = emailFingerprint(email);
			Long result = redisTemplate.execute(
				ISSUE_SCRIPT,
				List.of(
					challengeKey(purpose, emailFingerprint),
					cooldownKey(emailFingerprint),
					rateKey(emailFingerprint)
				),
				codeHash(purpose, emailFingerprint, code),
				String.valueOf(policy.challengeTtl().toMillis()),
				String.valueOf(policy.resendCooldown().toMillis()),
				String.valueOf(policy.rateLimitWindow().toMillis()),
				String.valueOf(policy.maxRequestsPerWindow())
			);
			if (Long.valueOf(0L).equals(result)) {
				return ChallengeIssueResult.ISSUED;
			}
			if (Long.valueOf(1L).equals(result)) {
				return ChallengeIssueResult.COOLDOWN;
			}
			return ChallengeIssueResult.RATE_LIMITED;
		} catch (EmailVerificationStoreException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	@Override
	public void cancelChallenge(EmailVerificationPurpose purpose, String email, String code) {
		try {
			String emailFingerprint = emailFingerprint(email);
			redisTemplate.execute(
				CANCEL_SCRIPT,
				List.of(challengeKey(purpose, emailFingerprint), cooldownKey(emailFingerprint)),
				codeHash(purpose, emailFingerprint, code)
			);
		} catch (EmailVerificationStoreException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	@Override
	public ChallengeVerificationResult confirmChallenge(
		EmailVerificationPurpose purpose,
		String email,
		String code,
		String grantToken,
		String grantSubject,
		EmailVerificationPolicy policy
	) {
		try {
			String emailFingerprint = emailFingerprint(email);
			Long result = redisTemplate.execute(
			CONFIRM_SCRIPT,
			List.of(
				challengeKey(purpose, emailFingerprint),
				grantKey(purpose, grantToken)
			),
			codeHash(purpose, emailFingerprint, code),
			grantSubject(purpose, grantSubject),
			String.valueOf(policy.grantTtl().toMillis()),
			String.valueOf(policy.maxAttempts())
		);
			if (Long.valueOf(0L).equals(result)) {
				return ChallengeVerificationResult.VERIFIED;
			}
			if (Long.valueOf(1L).equals(result)) {
				return ChallengeVerificationResult.INVALID;
			}
			if (Long.valueOf(2L).equals(result)) {
				return ChallengeVerificationResult.EXPIRED;
			}
			if (Long.valueOf(3L).equals(result)) {
				return ChallengeVerificationResult.ATTEMPTS_EXCEEDED;
			}
			return ChallengeVerificationResult.GRANT_COLLISION;
		} catch (EmailVerificationStoreException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	@Override
	public boolean consumeSignupGrant(String email, String grantToken) {
		try {
			Long result = redisTemplate.execute(
				CONSUME_SIGNUP_SCRIPT,
				List.of(grantKey(EmailVerificationPurpose.SIGNUP, grantToken)),
				grantSubject(EmailVerificationPurpose.SIGNUP, email)
			);
			return Long.valueOf(1L).equals(result);
		} catch (EmailVerificationStoreException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	@Override
	public OptionalLong consumePasswordResetGrant(String grantToken) {
		try {
			String subject = redisTemplate.execute(
				CONSUME_RESET_SCRIPT,
				List.of(grantKey(EmailVerificationPurpose.PASSWORD_RESET, grantToken))
			);
			if (subject == null) {
				return OptionalLong.empty();
			}
			try {
				return OptionalLong.of(Long.parseLong(subject));
			} catch (NumberFormatException exception) {
				return OptionalLong.empty();
			}
		} catch (EmailVerificationStoreException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	private EmailVerificationStoreException unavailable(RuntimeException exception) {
		return new EmailVerificationStoreException("Email verification store is unavailable", exception);
	}

	private String emailFingerprint(String email) {
		return hasher.hash("email-key", email);
	}

	private String codeHash(EmailVerificationPurpose purpose, String emailFingerprint, String code) {
		return hasher.hash("challenge-code:" + purpose.name() + ":" + emailFingerprint, code);
	}

	private String grantSubject(EmailVerificationPurpose purpose, String subject) {
		if (purpose == EmailVerificationPurpose.PASSWORD_RESET) {
			return subject;
		}
		return hasher.hash("signup-grant-subject", subject);
	}

	private String grantKey(EmailVerificationPurpose purpose, String token) {
		return "auth:email-verification:grant:" + purpose.name().toLowerCase()
			+ ":" + hasher.hash("grant-token:" + purpose.name(), token);
	}

	private String challengeKey(EmailVerificationPurpose purpose, String emailFingerprint) {
		return "auth:email-verification:challenge:" + purpose.name().toLowerCase() + ":" + emailFingerprint;
	}

	private String cooldownKey(String emailFingerprint) {
		return "auth:email-verification:cooldown:" + emailFingerprint;
	}

	private String rateKey(String emailFingerprint) {
		return "auth:email-verification:rate:" + emailFingerprint;
	}
}
