package com.faithlog.media.infrastructure.redis;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.service.port.MediaUploadRateLimitPort;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisMediaUploadRateLimitAdapter implements MediaUploadRateLimitPort {

	private static final String SCRIPT = """
		local userCount = tonumber(redis.call('GET', KEYS[1]) or '0')
		local campusCount = tonumber(redis.call('GET', KEYS[2]) or '0')
		local limit = tonumber(ARGV[1])
		if userCount >= limit or campusCount >= limit then return 0 end
		local nextUser = redis.call('INCR', KEYS[1])
		local nextCampus = redis.call('INCR', KEYS[2])
		if nextUser == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
		if nextCampus == 1 then redis.call('EXPIRE', KEYS[2], ARGV[2]) end
		return 1
		""";
	private static final DefaultRedisScript<Long> RATE_SCRIPT = new DefaultRedisScript<>(SCRIPT, Long.class);

	private final StringRedisTemplate redisTemplate;

	public RedisMediaUploadRateLimitAdapter(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public boolean acquire(Long campusId, Long userId) {
		try {
			Long result = redisTemplate.execute(
				RATE_SCRIPT,
				List.of("faithlog:media:upload:user:" + userId, "faithlog:media:upload:campus:" + campusId),
				"30", "600"
			);
			return Long.valueOf(1L).equals(result);
		} catch (RuntimeException exception) {
			throw new BusinessException(ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
		}
	}
}
