package com.faithlog.global.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "faithlog.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class Slf4jOperationalEventAdapter implements OperationalEventPort {

	private static final Logger log = LoggerFactory.getLogger(Slf4jOperationalEventAdapter.class);

	@Override
	public void databasePoolPending(int active, int maximum, int pending) {
		log.warn(
			"FAITHLOG_OBSERVABILITY event=DB_POOL_PENDING active={} maximum={} pending={}",
			active,
			maximum,
			pending
		);
	}

	@Override
	public void databasePoolHighUtilization(int active, int maximum) {
		log.warn(
			"FAITHLOG_OBSERVABILITY event=DB_POOL_HIGH_UTILIZATION active={} maximum={}",
			active,
			maximum
		);
	}

	@Override
	public void databasePoolTimeout(long count) {
		log.error("FAITHLOG_OBSERVABILITY event=DB_POOL_TIMEOUT count={}", count);
	}

	@Override
	public void externalServiceFailure(ExternalService service) {
		log.warn("FAITHLOG_OBSERVABILITY event=EXTERNAL_SERVICE_FAILURE service={}", service);
	}

	@Override
	public void authenticationFailure(AuthFlow flow, AuthFailure failure) {
		log.warn("FAITHLOG_OBSERVABILITY event=AUTH_FAILURE flow={} reason={}", flow, failure);
	}

	@Override
	public void schedulerSuccess(String jobName, int changedCount) {
		log.info(
			"FAITHLOG_OBSERVABILITY event=SCHEDULER_SUCCESS job={} changedCount={}",
			jobName,
			changedCount
		);
	}

	@Override
	public void schedulerFailure(String jobName) {
		log.error("FAITHLOG_OBSERVABILITY event=SCHEDULER_FAILURE job={}", jobName);
	}
}
