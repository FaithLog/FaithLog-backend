package com.faithlog.user.infrastructure.security;

import com.faithlog.user.service.port.VerificationCodeGenerator;
import java.security.SecureRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class SecureRandomVerificationCodeGenerator implements VerificationCodeGenerator {

	private static final int CODE_BOUND = 1_000_000;

	private final SecureRandom secureRandom = new SecureRandom();

	@Override
	public String generate() {
		return String.format("%06d", secureRandom.nextInt(CODE_BOUND));
	}
}
