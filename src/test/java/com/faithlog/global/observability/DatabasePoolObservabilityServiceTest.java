package com.faithlog.global.observability;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabasePoolObservabilityServiceTest {

	private HikariPoolMXBean pool;
	private HikariDataSource dataSource;
	private OperationalEventPort events;
	private DatabasePoolObservabilityService service;

	@BeforeEach
	void setUp() {
		pool = mock(HikariPoolMXBean.class);
		dataSource = mock(HikariDataSource.class);
		when(dataSource.getHikariPoolMXBean()).thenReturn(pool);
		events = mock(OperationalEventPort.class);
		service = new DatabasePoolObservabilityService(dataSource, events);
	}

	@Test
	void records_pending_connections_without_exposing_connection_details() {
		when(pool.getActiveConnections()).thenReturn(2);
		when(dataSource.getMaximumPoolSize()).thenReturn(5);
		when(pool.getThreadsAwaitingConnection()).thenReturn(1);

		service.sample();

		verify(events).databasePoolPending(2, 5, 1);
	}

	@Test
	void records_utilization_at_exactly_ninety_percent() {
		when(pool.getActiveConnections()).thenReturn(9);
		when(dataSource.getMaximumPoolSize()).thenReturn(10);
		when(pool.getThreadsAwaitingConnection()).thenReturn(0);

		service.sample();

		verify(events).databasePoolHighUtilization(9, 10);
	}

	@Test
	void does_not_record_pressure_below_approved_thresholds() {
		when(pool.getActiveConnections()).thenReturn(8);
		when(dataSource.getMaximumPoolSize()).thenReturn(10);
		when(pool.getThreadsAwaitingConnection()).thenReturn(0);

		service.sample();

		verify(events, never()).databasePoolPending(8, 10, 0);
		verify(events, never()).databasePoolHighUtilization(8, 10);
	}
}
