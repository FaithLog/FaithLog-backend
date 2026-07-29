package com.faithlog.user.service.command;

import com.faithlog.user.service.EmailVerificationPurpose;
import java.time.Duration;

public record QueueVerificationEmailCommand(
	EmailVerificationPurpose purpose,
	String recipientEmail,
	String verificationCode,
	Duration ttl,
	boolean deliveryRequired
) {
}
