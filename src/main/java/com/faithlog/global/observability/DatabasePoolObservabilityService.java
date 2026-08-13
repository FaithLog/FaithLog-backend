package com.faithlog.global.observability;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
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

	public DatabasePoolObservabilityService(HikariDataSource dataSource, OperationalEventPort events) {
		this.dataSource = dataSource;
		this.events = events;
	}

	@Scheduled(fixedDelayString = "${faithlog.observability.db-pool-sample-delay-ms:60000}")
	public void sample() {
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
}
