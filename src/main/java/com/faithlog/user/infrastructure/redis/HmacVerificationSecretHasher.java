package com.faithlog.user.infrastructure.redis;

import com.faithlog.user.service.port.EmailVerificationStoreException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacVerificationSecretHasher {

	private static final String ALGORITHM = "HmacSHA256";
	private static final HexFormat HEX = HexFormat.of();

	private final SecretKeySpec secretKey;

	public HmacVerificationSecretHasher(String secret) {
		this.secretKey = decodeSecret(secret);
	}

	public boolean isConfigured() {
		return secretKey != null;
	}

	public String hash(String context, String value) {
		if (secretKey == null) {
			throw new EmailVerificationStoreException("Email verification store is unavailable");
		}
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(secretKey);
			mac.update(context.getBytes(StandardCharsets.UTF_8));
			mac.update((byte) 0);
			return HEX.formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException exception) {
			throw new EmailVerificationStoreException("Email verification store is unavailable", exception);
		}
	}

	private SecretKeySpec decodeSecret(String secret) {
		if (secret == null || secret.isBlank()) {
			return null;
		}
		try {
			byte[] decoded = Base64.getDecoder().decode(secret);
			if (decoded.length < 32) {
				throw new EmailVerificationStoreException("Email verification store is unavailable");
			}
			return new SecretKeySpec(decoded, ALGORITHM);
		} catch (IllegalArgumentException exception) {
			throw new EmailVerificationStoreException("Email verification store is unavailable", exception);
		}
	}
}
