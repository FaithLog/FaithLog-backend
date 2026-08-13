package com.faithlog.global.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "faithlog.observability", name = "enabled", havingValue = "false")
public class NoOpOperationalEventAdapter implements OperationalEventPort {

	@Override
	public void databasePoolPending(int active, int maximum, int pending) {
	}

	@Override
	public void databasePoolHighUtilization(int active, int maximum) {
	}

	@Override
	public void databasePoolTimeout(long count) {
	}

	@Override
	public void externalServiceFailure(ExternalService service) {
	}

	@Override
	public void authenticationFailure(AuthFlow flow, AuthFailure failure) {
	}

	@Override
	public void schedulerSuccess(String jobName, int changedCount) {
	}

	@Override
	public void schedulerFailure(String jobName) {
	}
}
