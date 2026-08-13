package com.faithlog.user.service;

import com.faithlog.user.service.port.EmailDeliveryException;
import com.faithlog.user.service.port.EmailDispatchQueueException;
import com.faithlog.user.service.port.EmailDispatchStore;
import com.faithlog.user.service.port.EmailDispatchStore.EmailDispatchPayload;
import com.faithlog.user.service.port.EmailSenderPort;
import com.faithlog.user.service.port.OneTimeTokenGenerator;
import com.faithlog.global.observability.ExternalService;
import com.faithlog.global.observability.OperationalEventPort;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class EmailDispatchWorkerService {

	private static final Duration LEASE_TTL = Duration.ofMinutes(2);

	private final EmailDispatchStore dispatchStore;
	private final EmailSenderPort emailSenderPort;
	private final OneTimeTokenGenerator tokenGenerator;
	private final OperationalEventPort operationalEvents;

	public EmailDispatchWorkerService(
		EmailDispatchStore dispatchStore,
		EmailSenderPort emailSenderPort,
		OneTimeTokenGenerator tokenGenerator,
		OperationalEventPort operationalEvents
	) {
		this.dispatchStore = dispatchStore;
		this.emailSenderPort = emailSenderPort;
		this.tokenGenerator = tokenGenerator;
		this.operationalEvents = operationalEvents;
	}

	public void dispatch(String dispatchToken) {
		String leaseToken = tokenGenerator.generate();
		var acquisition = dispatchStore.acquire(dispatchToken, leaseToken, LEASE_TTL);
		if (acquisition.status() == EmailDispatchStore.AcquisitionStatus.MISSING) {
			return;
		}
		if (acquisition.status() == EmailDispatchStore.AcquisitionStatus.IN_PROGRESS) {
			throw new EmailDispatchQueueException("Email dispatch is already in progress");
		}
		EmailDispatchPayload payload = acquisition.payload();
		try {
			if (payload.deliveryRequired()) {
				emailSenderPort.sendVerificationCode(
					deliveryId(dispatchToken),
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
			if (exception instanceof EmailDeliveryException) {
				operationalEvents.externalServiceFailure(ExternalService.BREVO);
			}
			dispatchStore.release(dispatchToken, leaseToken);
			throw exception;
		} catch (RuntimeException exception) {
			dispatchStore.release(dispatchToken, leaseToken);
			throw new EmailDispatchQueueException("Email dispatch is unavailable", exception);
		}
	}

	private String deliveryId(String dispatchToken) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256")
					.digest(dispatchToken.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new EmailDispatchQueueException("Email dispatch is unavailable", exception);
		}
	}
}
