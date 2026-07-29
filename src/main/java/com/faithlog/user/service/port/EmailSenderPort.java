package com.faithlog.user.service.port;

import com.faithlog.user.service.EmailVerificationPurpose;
import java.time.Duration;

public interface EmailSenderPort {

	void sendVerificationCode(
		EmailVerificationPurpose purpose,
		String recipientEmail,
		String verificationCode,
		Duration ttl
	);
}
