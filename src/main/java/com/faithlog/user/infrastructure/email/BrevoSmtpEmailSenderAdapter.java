package com.faithlog.user.infrastructure.email;

import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.port.EmailDeliveryException;
import com.faithlog.user.service.port.EmailSenderPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public class BrevoSmtpEmailSenderAdapter implements EmailSenderPort {

	private static final String DELIVERY_ID_HEADER = "X-FaithLog-Delivery-Id";
	private static final String LOGO_CONTENT_ID = "faithlog-logo";
	private static final Pattern DELIVERY_ID_PATTERN = Pattern.compile("[0-9a-f]{64}");
	private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile("[0-9]{6}");
	private static final String INVALID_REQUEST_MESSAGE = "Email delivery request is invalid";
	private static final String PROVIDER_UNAVAILABLE_MESSAGE = "Email delivery provider is unavailable";

	private final JavaMailSender mailSender;
	private final String senderName;
	private final String senderEmail;
	private final byte[] logo;

	public BrevoSmtpEmailSenderAdapter(
		JavaMailSender mailSender,
		String senderName,
		String senderEmail,
		Resource logoResource,
		String expectedLogoSha256
	) {
		this.mailSender = Objects.requireNonNull(mailSender, "mailSender must not be null");
		this.senderName = requireSafeDisplayName(senderName);
		this.senderEmail = requirePlainAddress(senderEmail, "sender email");
		this.logo = readAndVerifyLogo(logoResource, expectedLogoSha256);
	}

	@Override
	public void sendVerificationCode(
		String deliveryId,
		EmailVerificationPurpose purpose,
		String recipientEmail,
		String verificationCode,
		Duration ttl
	) {
		String safeDeliveryId = requireMatch(deliveryId, DELIVERY_ID_PATTERN);
		EmailVerificationPurpose safePurpose = requirePurpose(purpose);
		String safeRecipient = requireRequestAddress(recipientEmail);
		String safeCode = requireMatch(verificationCode, VERIFICATION_CODE_PATTERN);
		long ttlMinutes = requirePositiveTtlMinutes(ttl);

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(
				message,
				MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
				StandardCharsets.UTF_8.name()
			);
			helper.setFrom(senderEmail, senderName);
			helper.setTo(safeRecipient);
			helper.setSubject(subject(safePurpose));
			helper.setText(plainText(safePurpose, safeCode, ttlMinutes), htmlText(safePurpose, safeCode, ttlMinutes));
			helper.addInline(LOGO_CONTENT_ID, new ByteArrayResource(logo), "image/png");
			message.setHeader(DELIVERY_ID_HEADER, safeDeliveryId);
			message.saveChanges();
			mailSender.send(message);
		} catch (MessagingException | IOException | MailException exception) {
			throw new EmailDeliveryException(PROVIDER_UNAVAILABLE_MESSAGE);
		}
	}

	private static String subject(EmailVerificationPurpose purpose) {
		return switch (purpose) {
			case SIGNUP -> "FaithLog 회원가입 이메일 인증번호";
			case PASSWORD_RESET -> "FaithLog 비밀번호 재설정 인증번호";
		};
	}

	private static String purposeCopy(EmailVerificationPurpose purpose) {
		return switch (purpose) {
			case SIGNUP -> "회원가입";
			case PASSWORD_RESET -> "비밀번호 재설정";
		};
	}

	private static String plainText(EmailVerificationPurpose purpose, String code, long ttlMinutes) {
		return """
			FaithLog %s 인증번호

			아래 인증번호를 FaithLog 앱에 입력해 주세요.

			%s

			인증번호는 %d분 동안 유효합니다.
			본인이 요청하지 않았다면 이 메일을 무시해 주세요.
			""".formatted(purposeCopy(purpose), code, ttlMinutes);
	}

	private static String htmlText(EmailVerificationPurpose purpose, String code, long ttlMinutes) {
		return """
			<!doctype html>
			<html lang="ko">
			<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
			<body style="margin:0;background:#f4f7fb;color:#172033;font-family:Arial,'Apple SD Gothic Neo',sans-serif;">
			  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f7fb;padding:32px 16px;">
			    <tr><td align="center">
			      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:520px;background:#ffffff;border:1px solid #e3e9f2;border-radius:8px;">
			        <tr><td style="padding:32px;text-align:center;">
			          <img src="cid:faithlog-logo" alt="FaithLog" width="72" height="72" style="display:block;margin:0 auto 20px;">
			          <h1 style="margin:0 0 12px;font-size:22px;line-height:1.4;color:#172033;">%s 인증번호</h1>
			          <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#526079;">아래 인증번호를 FaithLog 앱에 입력해 주세요.</p>
			          <p aria-label="인증번호" style="margin:0 0 24px;padding:18px;background:#eef4ff;border-radius:8px;font-size:32px;font-weight:700;letter-spacing:8px;color:#175cd3;">%s</p>
			          <p style="margin:0;font-size:14px;line-height:1.7;color:#667085;">인증번호는 <strong>%d분</strong> 동안 유효합니다.<br>본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>
			        </td></tr>
			      </table>
			    </td></tr>
			  </table>
			</body>
			</html>
			""".formatted(purposeCopy(purpose), code, ttlMinutes);
	}

	private static String requireSafeDisplayName(String value) {
		if (value == null || value.isBlank() || !value.equals(value.trim()) || containsControl(value)) {
			throw new IllegalArgumentException("sender name is invalid");
		}
		return value;
	}

	private static String requireRequestAddress(String value) {
		try {
			return requirePlainAddress(value, "recipient email");
		} catch (IllegalArgumentException exception) {
			throw new EmailDeliveryException(INVALID_REQUEST_MESSAGE);
		}
	}

	private static String requirePlainAddress(String value, String field) {
		if (value == null || value.isBlank() || !value.equals(value.trim()) || containsControl(value)) {
			throw new IllegalArgumentException(field + " is invalid");
		}
		try {
			InternetAddress address = new InternetAddress(value, true);
			address.validate();
			if (address.getPersonal() != null || !value.equals(address.getAddress())) {
				throw new IllegalArgumentException(field + " is invalid");
			}
			return value;
		} catch (jakarta.mail.internet.AddressException exception) {
			throw new IllegalArgumentException(field + " is invalid");
		}
	}

	private static boolean containsControl(String value) {
		return value.chars().anyMatch(Character::isISOControl);
	}

	private static String requireMatch(String value, Pattern pattern) {
		if (value == null || !pattern.matcher(value).matches()) {
			throw new EmailDeliveryException(INVALID_REQUEST_MESSAGE);
		}
		return value;
	}

	private static EmailVerificationPurpose requirePurpose(EmailVerificationPurpose purpose) {
		if (purpose == null) {
			throw new EmailDeliveryException(INVALID_REQUEST_MESSAGE);
		}
		return purpose;
	}

	private static long requirePositiveTtlMinutes(Duration ttl) {
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw new EmailDeliveryException(INVALID_REQUEST_MESSAGE);
		}
		long seconds = ttl.getSeconds();
		long minutes = seconds / 60;
		if (seconds % 60 != 0 || ttl.getNano() != 0) {
			minutes++;
		}
		return Math.max(minutes, 1);
	}

	private static byte[] readAndVerifyLogo(Resource resource, String expectedSha256) {
		if (resource == null || expectedSha256 == null || !DELIVERY_ID_PATTERN.matcher(expectedSha256).matches()) {
			throw new IllegalArgumentException("logo configuration is invalid");
		}
		try {
			byte[] bytes = resource.getContentAsByteArray();
			if (bytes.length == 0 || !sha256(bytes).equals(expectedSha256)) {
				throw new IllegalArgumentException("logo configuration is invalid");
			}
			return bytes.clone();
		} catch (IOException exception) {
			throw new IllegalArgumentException("logo configuration is invalid");
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable");
		}
	}
}
