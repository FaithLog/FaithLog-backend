package com.faithlog.user.service.port;

import com.faithlog.user.service.EmailVerificationPurpose;
import java.time.Duration;

public interface EmailDispatchStore {

	String create(EmailDispatchPayload payload, Duration ttl);

	EmailDispatchAcquisition acquire(String dispatchToken, String leaseToken, Duration leaseTtl);

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

	enum AcquisitionStatus {
		ACQUIRED,
		IN_PROGRESS,
		MISSING
	}

	record EmailDispatchAcquisition(
		AcquisitionStatus status,
		EmailDispatchPayload payload
	) {
		public EmailDispatchAcquisition {
			if ((status == AcquisitionStatus.ACQUIRED) != (payload != null)) {
				throw new IllegalArgumentException("Email dispatch acquisition state is invalid");
			}
		}

		public static EmailDispatchAcquisition acquired(EmailDispatchPayload payload) {
			return new EmailDispatchAcquisition(AcquisitionStatus.ACQUIRED, payload);
		}

		public static EmailDispatchAcquisition inProgress() {
			return new EmailDispatchAcquisition(AcquisitionStatus.IN_PROGRESS, null);
		}

		public static EmailDispatchAcquisition missing() {
			return new EmailDispatchAcquisition(AcquisitionStatus.MISSING, null);
		}
	}
}
