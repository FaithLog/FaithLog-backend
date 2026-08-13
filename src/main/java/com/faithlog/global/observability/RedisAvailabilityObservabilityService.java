package com.faithlog.global.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "faithlog.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisAvailabilityObservabilityService {

	private final RedisConnectionFactory connectionFactory;
	private final OperationalEventPort events;

	public RedisAvailabilityObservabilityService(
		RedisConnectionFactory connectionFactory,
		OperationalEventPort events
	) {
		this.connectionFactory = connectionFactory;
		this.events = events;
	}

	@Scheduled(fixedDelayString = "${faithlog.observability.redis-probe-delay-ms:60000}")
	public void probe() {
		try (RedisConnection connection = connectionFactory.getConnection()) {
			if (!"PONG".equals(connection.ping())) {
				events.externalServiceFailure(ExternalService.UPSTASH_REDIS);
			}
		} catch (RuntimeException exception) {
			events.externalServiceFailure(ExternalService.UPSTASH_REDIS);
		}
	}
}
