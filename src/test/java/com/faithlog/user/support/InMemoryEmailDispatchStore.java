package com.faithlog.user.support;

import com.faithlog.user.service.port.EmailDispatchStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryEmailDispatchStore implements EmailDispatchStore {

	private final Map<String, EmailDispatchPayload> payloads = new HashMap<>();
	private final Map<String, String> leases = new HashMap<>();

	@Override
	public synchronized String create(EmailDispatchPayload payload, Duration ttl) {
		String token = UUID.randomUUID().toString();
		payloads.put(token, payload);
		return token;
	}

	@Override
	public synchronized Optional<EmailDispatchPayload> acquire(
		String dispatchToken,
		String leaseToken,
		Duration leaseTtl
	) {
		EmailDispatchPayload payload = payloads.get(dispatchToken);
		if (payload == null || leases.putIfAbsent(dispatchToken, leaseToken) != null) {
			return Optional.empty();
		}
		return Optional.of(payload);
	}

	@Override
	public synchronized boolean acknowledge(String dispatchToken, String leaseToken) {
		if (!leaseToken.equals(leases.get(dispatchToken))) {
			return false;
		}
		leases.remove(dispatchToken);
		return payloads.remove(dispatchToken) != null;
	}

	@Override
	public synchronized void release(String dispatchToken, String leaseToken) {
		leases.remove(dispatchToken, leaseToken);
	}

	@Override
	public synchronized void discard(String dispatchToken) {
		leases.remove(dispatchToken);
		payloads.remove(dispatchToken);
	}
}
