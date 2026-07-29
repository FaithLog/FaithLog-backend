package com.faithlog.user.infrastructure.security;

import com.faithlog.user.service.port.OneTimeTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class SecureRandomOneTimeTokenGenerator implements OneTimeTokenGenerator {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	@Override
	public String generate() {
		byte[] token = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(token);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}
}
