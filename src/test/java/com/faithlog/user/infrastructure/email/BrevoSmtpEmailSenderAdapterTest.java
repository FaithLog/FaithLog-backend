package com.faithlog.user.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.port.EmailDeliveryException;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class BrevoSmtpEmailSenderAdapterTest {

	private static final byte[] LOGO = "approved-png".getBytes(StandardCharsets.UTF_8);
	private static final String LOGO_SHA256 = "9518b9a0f667a993464b18ef545b2b449c090608234e1a9daf7a6743e1a0614f";
	private static final String DELIVERY_ID =
		"b71dedf9efc1ee2c596f20449a0d8b7d0070789043eaf925b70f5f4c5658d6c9";

	@Mock
	private JavaMailSender mailSender;

	private MimeMessage message;
	private BrevoSmtpEmailSenderAdapter adapter;

	@BeforeEach
	void setUp() {
		message = new MimeMessage(Session.getInstance(new Properties()));
		lenient().when(mailSender.createMimeMessage()).thenReturn(message);
		adapter = new BrevoSmtpEmailSenderAdapter(
			mailSender,
			"FaithLog",
			"josephuk77@gmail.com",
			new ByteArrayResource(LOGO),
			LOGO_SHA256
		);
	}

	@Test
	void sends_signup_code_as_branded_multipart_message_with_plaintext_fallback() throws Exception {
		adapter.sendVerificationCode(
			DELIVERY_ID,
			EmailVerificationPurpose.SIGNUP,
			"member@example.com",
			"123456",
			Duration.ofMinutes(5)
		);

		verify(mailSender).send(message);
		assertThat(message.getSubject()).isEqualTo("FaithLog 회원가입 이메일 인증번호");
		assertThat(message.getFrom()[0].toString()).isEqualTo("FaithLog <josephuk77@gmail.com>");
		assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString())
			.isEqualTo("member@example.com");
		assertThat(message.getHeader("X-FaithLog-Delivery-Id", null)).isEqualTo(DELIVERY_ID);

		List<BodyPart> parts = flatten(message.getContent());
		assertThat(parts).anySatisfy(part -> {
			assertThat(part.getContentType()).startsWith("text/plain");
			assertThat(part.getContent().toString())
				.contains("회원가입")
				.contains("123456")
				.contains("5분")
				.doesNotContain("member@example.com");
		});
		assertThat(parts).anySatisfy(part -> {
			assertThat(part.getContentType()).startsWith("text/html");
			assertThat(part.getContent().toString())
				.contains("cid:faithlog-logo")
				.contains("alt=\"FaithLog\"")
				.contains("123456")
				.doesNotContain("http://", "https://", "member@example.com");
		});
		assertThat(parts).anySatisfy(part -> {
			assertThat(part.getDisposition()).isEqualTo("inline");
			assertThat(part.getContentType()).startsWith("image/png");
			assertThat(part.getHeader("Content-ID")[0]).isEqualTo("<faithlog-logo>");
		});
	}

	@Test
	void sends_password_reset_with_purpose_specific_copy() throws Exception {
		adapter.sendVerificationCode(
			DELIVERY_ID,
			EmailVerificationPurpose.PASSWORD_RESET,
			"member@example.com",
			"654321",
			Duration.ofMinutes(5)
		);

		assertThat(message.getSubject()).isEqualTo("FaithLog 비밀번호 재설정 인증번호");
		assertThat(flatten(message.getContent()))
			.anySatisfy(part -> assertThat(part.getContent().toString()).contains("비밀번호 재설정", "654321"));
	}

	@Test
	void rejects_header_injection_and_invalid_verification_material_before_sending() {
		assertThatThrownBy(() -> adapter.sendVerificationCode(
			DELIVERY_ID + "\r\nX-Evil: true",
			EmailVerificationPurpose.SIGNUP,
			"member@example.com",
			"123456",
			Duration.ofMinutes(5)
		)).isInstanceOf(EmailDeliveryException.class);

		assertThatThrownBy(() -> adapter.sendVerificationCode(
			DELIVERY_ID,
			EmailVerificationPurpose.SIGNUP,
			"member@example.com",
			"12345A",
			Duration.ofMinutes(5)
		)).isInstanceOf(EmailDeliveryException.class);

		assertThatThrownBy(() -> adapter.sendVerificationCode(
			DELIVERY_ID,
			EmailVerificationPurpose.SIGNUP,
			"member@example.com\r\nBcc: attacker@example.com",
			"123456",
			Duration.ofMinutes(5)
		)).isInstanceOf(EmailDeliveryException.class);

		assertThatThrownBy(() -> adapter.sendVerificationCode(
			DELIVERY_ID,
			EmailVerificationPurpose.SIGNUP,
			"member@example.com",
			"123456",
			Duration.ZERO
		)).isInstanceOf(EmailDeliveryException.class);

		verifyNoInteractions(mailSender);
	}

	@Test
	void rejects_invalid_sender_and_logo_configuration() {
		assertThatThrownBy(() -> new BrevoSmtpEmailSenderAdapter(
			mailSender,
			"FaithLog\r\nBcc: attacker@example.com",
			"josephuk77@gmail.com",
			new ByteArrayResource(LOGO),
			LOGO_SHA256
		)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new BrevoSmtpEmailSenderAdapter(
			mailSender,
			"FaithLog",
			"FaithLog <josephuk77@gmail.com>",
			new ByteArrayResource(LOGO),
			LOGO_SHA256
		)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new BrevoSmtpEmailSenderAdapter(
			mailSender,
			"FaithLog",
			"josephuk77@gmail.com",
			new ByteArrayResource(LOGO),
			"0".repeat(64)
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void wraps_provider_failure_without_recipient_or_code_in_the_exception() {
		org.mockito.Mockito.doThrow(new MailSendException("smtp rejected member@example.com 123456"))
			.when(mailSender).send(message);

		assertThatThrownBy(() -> adapter.sendVerificationCode(
			DELIVERY_ID,
			EmailVerificationPurpose.SIGNUP,
			"member@example.com",
			"123456",
			Duration.ofMinutes(5)
		))
			.isInstanceOf(EmailDeliveryException.class)
			.hasMessage("Email delivery provider is unavailable")
			.hasMessageNotContaining("member@example.com")
			.hasMessageNotContaining("123456");
	}

	private List<BodyPart> flatten(Object content) throws Exception {
		List<BodyPart> result = new ArrayList<>();
		flatten(content, result);
		return result;
	}

	private void flatten(Object content, List<BodyPart> result) throws Exception {
		if (!(content instanceof Multipart multipart)) {
			return;
		}
		for (int index = 0; index < multipart.getCount(); index++) {
			BodyPart part = multipart.getBodyPart(index);
			result.add(part);
			flatten(part.getContent(), result);
		}
	}
}
