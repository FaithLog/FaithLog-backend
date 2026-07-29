package com.faithlog.user.infrastructure.email;

import com.faithlog.user.service.port.EmailDispatchQueueException;
import com.faithlog.user.service.port.EmailDispatchStore;
import java.time.Duration;
import java.util.Optional;

public class UnavailableEmailDispatchStore implements EmailDispatchStore {

	@Override
	public String create(EmailDispatchPayload payload, Duration ttl) {
		throw unavailable();
	}

	@Override
	public Optional<EmailDispatchPayload> acquire(String dispatchToken, String leaseToken, Duration leaseTtl) {
		throw unavailable();
	}

	@Override
	public boolean acknowledge(String dispatchToken, String leaseToken) {
		throw unavailable();
	}

	@Override
	public void release(String dispatchToken, String leaseToken) {
		throw unavailable();
	}

	@Override
	public void discard(String dispatchToken) {
		throw unavailable();
	}

	private EmailDispatchQueueException unavailable() {
		return new EmailDispatchQueueException("Email dispatch store is not configured");
	}
}
