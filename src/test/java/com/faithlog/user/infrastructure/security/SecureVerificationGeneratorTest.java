package com.faithlog.user.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureVerificationGeneratorTest {

	@Test
	void verification_codes_are_always_six_digits_and_tokens_are_url_safe_and_high_entropy() {
		SecureRandomVerificationCodeGenerator codeGenerator = new SecureRandomVerificationCodeGenerator();
		SecureRandomOneTimeTokenGenerator tokenGenerator = new SecureRandomOneTimeTokenGenerator();
		Set<String> codes = new HashSet<>();
		Set<String> tokens = new HashSet<>();

		for (int index = 0; index < 100; index++) {
			codes.add(codeGenerator.generate());
			tokens.add(tokenGenerator.generate());
		}

		assertThat(codes).allMatch(code -> code.matches("\\d{6}"));
		assertThat(tokens)
			.hasSize(100)
			.allMatch(token -> token.matches("[A-Za-z0-9_-]{43}"));
	}
}
