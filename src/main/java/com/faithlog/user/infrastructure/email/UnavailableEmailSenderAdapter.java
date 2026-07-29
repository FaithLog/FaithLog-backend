package com.faithlog.user.infrastructure.email;

import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.port.EmailDeliveryException;
import com.faithlog.user.service.port.EmailSenderPort;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class UnavailableEmailSenderAdapter implements EmailSenderPort {

	@Override
	public void sendVerificationCode(
		String deliveryId,
		EmailVerificationPurpose purpose,
		String email,
		String code,
		Duration ttl
	) {
		throw new EmailDeliveryException("Email delivery provider is not configured");
	}
}
