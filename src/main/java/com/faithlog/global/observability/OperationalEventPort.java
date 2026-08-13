package com.faithlog.global.observability;

public interface OperationalEventPort {

	void databasePoolPending(int active, int maximum, int pending);

	void databasePoolHighUtilization(int active, int maximum);

	void externalServiceFailure(ExternalService service);

	void authenticationFailure(AuthFlow flow, AuthFailure failure);

	void schedulerSuccess(String jobName, int changedCount);

	void schedulerFailure(String jobName);

	static OperationalEventPort noop() {
		return NoOpOperationalEventPort.INSTANCE;
	}

	final class NoOpOperationalEventPort implements OperationalEventPort {
		private static final NoOpOperationalEventPort INSTANCE = new NoOpOperationalEventPort();

		private NoOpOperationalEventPort() {
		}

		@Override
		public void databasePoolPending(int active, int maximum, int pending) {
		}

		@Override
		public void databasePoolHighUtilization(int active, int maximum) {
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
}
