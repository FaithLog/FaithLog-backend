package com.faithlog.global.observability;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class RedisAvailabilityObservabilityServiceTest {

	private RedisConnectionFactory connectionFactory;
	private OperationalEventPort events;
	private RedisAvailabilityObservabilityService service;

	@BeforeEach
	void setUp() {
		connectionFactory = mock(RedisConnectionFactory.class);
		events = mock(OperationalEventPort.class);
		service = new RedisAvailabilityObservabilityService(connectionFactory, events);
	}

	@Test
	void records_upstash_probe_failure_without_provider_error_text() {
		when(connectionFactory.getConnection())
			.thenThrow(new RedisConnectionFailureException("secret provider response"));

		service.probe();

		verify(events).externalServiceFailure(ExternalService.UPSTASH_REDIS);
	}

	@Test
	void successful_ping_does_not_record_failure() {
		RedisConnection connection = mock(RedisConnection.class);
		when(connectionFactory.getConnection()).thenReturn(connection);
		when(connection.ping()).thenReturn("PONG");

		service.probe();

		verify(connection).close();
		verify(events, never()).externalServiceFailure(ExternalService.UPSTASH_REDIS);
	}
}
