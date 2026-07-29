package com.faithlog.user.service.port;

import com.faithlog.user.service.EmailVerificationPurpose;
import java.time.Duration;
import java.util.Optional;

public interface EmailDispatchStore {

	String create(EmailDispatchPayload payload, Duration ttl);

	Optional<EmailDispatchPayload> acquire(String dispatchToken, String leaseToken, Duration leaseTtl);

	boolean acknowledge(String dispatchToken, String leaseToken);

	void release(String dispatchToken, String leaseToken);

	void discard(String dispatchToken);

	record EmailDispatchPayload(
		EmailVerificationPurpose purpose,
		String recipientEmail,
		String verificationCode,
		long ttlSeconds,
		boolean deliveryRequired
	) {
	}
}
