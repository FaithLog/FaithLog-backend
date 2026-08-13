package com.faithlog.global.observability;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(HikariDataSource.class)
@ConditionalOnProperty(prefix = "faithlog.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatabasePoolObservabilityService {

	private static final int HIGH_UTILIZATION_PERCENT = 90;

	private final HikariDataSource dataSource;
	private final OperationalEventPort events;
	private final MeterRegistry meterRegistry;
	private Double previousTimeoutCount;

	public DatabasePoolObservabilityService(
		HikariDataSource dataSource,
		OperationalEventPort events,
		MeterRegistry meterRegistry
	) {
		this.dataSource = dataSource;
		this.events = events;
		this.meterRegistry = meterRegistry;
	}

	@Scheduled(fixedDelayString = "${faithlog.observability.db-pool-sample-delay-ms:60000}")
	public void sample() {
		recordNewTimeouts();
		HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
		if (pool == null) {
			return;
		}
		int active = pool.getActiveConnections();
		int maximum = dataSource.getMaximumPoolSize();
		int pending = pool.getThreadsAwaitingConnection();
		if (pending > 0) {
			events.databasePoolPending(active, maximum, pending);
		}
		if (maximum > 0 && (long) active * 100 >= (long) maximum * HIGH_UTILIZATION_PERCENT) {
			events.databasePoolHighUtilization(active, maximum);
		}
	}

	private void recordNewTimeouts() {
		Counter timeoutCounter = meterRegistry.find("hikaricp.connections.timeout").counter();
		if (timeoutCounter == null) {
			return;
		}
		double current = timeoutCounter.count();
		if (previousTimeoutCount != null && current > previousTimeoutCount) {
			events.databasePoolTimeout((long) (current - previousTimeoutCount));
		}
		previousTimeoutCount = current;
	}
}
