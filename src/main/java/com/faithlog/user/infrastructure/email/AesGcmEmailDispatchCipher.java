package com.faithlog.user.infrastructure.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.service.port.EmailDispatchQueueException;
import com.faithlog.user.service.port.EmailDispatchStore.EmailDispatchPayload;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcmEmailDispatchCipher {

	private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final byte[] AAD = "faithlog-email-dispatch-v1".getBytes(StandardCharsets.UTF_8);
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec encryptionKey;
	private final SecretKeySpec fingerprintKey;
	private final ObjectMapper objectMapper;
	private final SecureRandom secureRandom;

	public AesGcmEmailDispatchCipher(String base64Key, ObjectMapper objectMapper) {
		this(base64Key, objectMapper, new SecureRandom());
	}

	AesGcmEmailDispatchCipher(String base64Key, ObjectMapper objectMapper, SecureRandom secureRandom) {
		byte[] key = decodeKey(base64Key);
		this.encryptionKey = new SecretKeySpec(key, "AES");
		this.fingerprintKey = new SecretKeySpec(key, HMAC_ALGORITHM);
		this.objectMapper = objectMapper;
		this.secureRandom = secureRandom;
	}

	public String encrypt(EmailDispatchPayload payload) {
		try {
			byte[] iv = new byte[IV_BYTES];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(AAD);
			byte[] ciphertext = cipher.doFinal(objectMapper.writeValueAsBytes(payload));
			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
		} catch (GeneralSecurityException | java.io.IOException exception) {
			throw unavailable(exception);
		}
	}

	public EmailDispatchPayload decrypt(String encryptedPayload) {
		try {
			byte[] combined = Base64.getUrlDecoder().decode(encryptedPayload);
			if (combined.length <= IV_BYTES) {
				throw new EmailDispatchQueueException("Email dispatch payload is unavailable");
			}
			byte[] iv = java.util.Arrays.copyOfRange(combined, 0, IV_BYTES);
			byte[] ciphertext = java.util.Arrays.copyOfRange(combined, IV_BYTES, combined.length);
			Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(AAD);
			return objectMapper.readValue(cipher.doFinal(ciphertext), EmailDispatchPayload.class);
		} catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException exception) {
			throw unavailable(exception);
		}
	}

	public String fingerprint(String dispatchToken) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(fingerprintKey);
			mac.update(AAD);
			mac.update((byte) 0);
			return java.util.HexFormat.of().formatHex(
				mac.doFinal(dispatchToken.getBytes(StandardCharsets.UTF_8))
			);
		} catch (GeneralSecurityException exception) {
			throw unavailable(exception);
		}
	}

	private byte[] decodeKey(String base64Key) {
		try {
			byte[] key = Base64.getDecoder().decode(base64Key == null ? "" : base64Key);
			if (key.length != 32) {
				throw new EmailDispatchQueueException("Email dispatch encryption is unavailable");
			}
			return key;
		} catch (IllegalArgumentException exception) {
			throw unavailable(exception);
		}
	}

	private EmailDispatchQueueException unavailable(Exception exception) {
		return new EmailDispatchQueueException("Email dispatch payload is unavailable", exception);
	}
}
