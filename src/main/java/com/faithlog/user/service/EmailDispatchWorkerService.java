package com.faithlog.user.service;

import com.faithlog.user.service.port.EmailDeliveryException;
import com.faithlog.user.service.port.EmailDispatchQueueException;
import com.faithlog.user.service.port.EmailDispatchStore;
import com.faithlog.user.service.port.EmailDispatchStore.EmailDispatchPayload;
import com.faithlog.user.service.port.EmailSenderPort;
import com.faithlog.user.service.port.OneTimeTokenGenerator;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class EmailDispatchWorkerService {

	private static final Duration LEASE_TTL = Duration.ofMinutes(2);

	private final EmailDispatchStore dispatchStore;
	private final EmailSenderPort emailSenderPort;
	private final OneTimeTokenGenerator tokenGenerator;

	public EmailDispatchWorkerService(
		EmailDispatchStore dispatchStore,
		EmailSenderPort emailSenderPort,
		OneTimeTokenGenerator tokenGenerator
	) {
		this.dispatchStore = dispatchStore;
		this.emailSenderPort = emailSenderPort;
		this.tokenGenerator = tokenGenerator;
	}

	public void dispatch(String dispatchToken) {
		String leaseToken = tokenGenerator.generate();
		var acquired = dispatchStore.acquire(dispatchToken, leaseToken, LEASE_TTL);
		if (acquired.isEmpty()) {
			return;
		}
		EmailDispatchPayload payload = acquired.get();
		try {
			if (payload.deliveryRequired()) {
				emailSenderPort.sendVerificationCode(
					payload.purpose(),
					payload.recipientEmail(),
					payload.verificationCode(),
					Duration.ofSeconds(payload.ttlSeconds())
				);
			}
			if (!dispatchStore.acknowledge(dispatchToken, leaseToken)) {
				throw new EmailDispatchQueueException("Email dispatch acknowledgement failed");
			}
		} catch (EmailDeliveryException | EmailDispatchQueueException exception) {
			dispatchStore.release(dispatchToken, leaseToken);
			throw exception;
		} catch (RuntimeException exception) {
			dispatchStore.release(dispatchToken, leaseToken);
			throw new EmailDispatchQueueException("Email dispatch is unavailable", exception);
		}
	}
}
