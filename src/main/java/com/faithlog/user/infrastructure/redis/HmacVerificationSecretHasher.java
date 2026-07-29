package com.faithlog.user.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacVerificationSecretHasher {

	private static final String ALGORITHM = "HmacSHA256";
	private static final HexFormat HEX = HexFormat.of();

	private final SecretKeySpec secretKey;

	public HmacVerificationSecretHasher(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("Email verification HMAC secret must not be blank");
		}
		this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	public String hash(String context, String value) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(secretKey);
			mac.update(context.getBytes(StandardCharsets.UTF_8));
			mac.update((byte) 0);
			return HEX.formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
		}
	}
}
