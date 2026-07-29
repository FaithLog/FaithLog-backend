package com.faithlog.user.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.user.infrastructure.email.BrevoSmtpEmailSenderAdapter;
import com.faithlog.user.infrastructure.email.UnavailableEmailSenderAdapter;
import com.faithlog.user.service.port.EmailSenderPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class BrevoSmtpConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.profiles.active=brevo-config-test")
		.withUserConfiguration(BrevoSmtpConfiguration.class, UnavailableEmailSenderAdapter.class);

	@Test
	void selects_exactly_one_sender_adapter_from_the_provider_flag() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(EmailSenderPort.class);
			assertThat(context.getBean(EmailSenderPort.class)).isInstanceOf(UnavailableEmailSenderAdapter.class);
		});

		contextRunner
			.withPropertyValues(approvedRuntimeProperties())
			.run(context -> {
				assertThat(context).hasSingleBean(EmailSenderPort.class);
				assertThat(context.getBean(EmailSenderPort.class)).isInstanceOf(BrevoSmtpEmailSenderAdapter.class);
			});
	}

	@Test
	void fails_context_startup_when_provider_is_enabled_without_smtp_secret() {
		contextRunner
			.withPropertyValues(
				"faithlog.auth.email-provider.enabled=true",
				"spring.mail.host=smtp-relay.brevo.com",
				"spring.mail.port=587",
				"spring.mail.username=b3a5e2001@smtp-brevo.com"
			)
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void creates_sender_only_from_the_approved_brevo_starttls_contract() {
		MailProperties properties = approvedProperties();

		JavaMailSenderImpl sender = BrevoSmtpConfiguration.createMailSender(properties);

		assertThat(sender.getHost()).isEqualTo("smtp-relay.brevo.com");
		assertThat(sender.getPort()).isEqualTo(587);
		assertThat(sender.getUsername()).isEqualTo("b3a5e2001@smtp-brevo.com");
		assertThat(sender.getPassword()).isEqualTo("runtime-secret");
		assertThat(sender.getJavaMailProperties())
			.containsEntry("mail.smtp.auth", "true")
			.containsEntry("mail.smtp.starttls.enable", "true")
			.containsEntry("mail.smtp.starttls.required", "true")
			.containsEntry("mail.smtp.connectiontimeout", "5000")
			.containsEntry("mail.smtp.timeout", "10000")
			.containsEntry("mail.smtp.writetimeout", "10000");
	}

	@Test
	void rejects_missing_or_non_brevo_credentials_and_insecure_transport() {
		MailProperties missingPassword = approvedProperties();
		missingPassword.setPassword("");
		assertThatThrownBy(() -> BrevoSmtpConfiguration.createMailSender(missingPassword))
			.isInstanceOf(IllegalStateException.class);

		MailProperties wrongHost = approvedProperties();
		wrongHost.setHost("smtp.example.com");
		assertThatThrownBy(() -> BrevoSmtpConfiguration.createMailSender(wrongHost))
			.isInstanceOf(IllegalStateException.class);

		MailProperties insecure = approvedProperties();
		insecure.getProperties().put("mail.smtp.starttls.required", "false");
		assertThatThrownBy(() -> BrevoSmtpConfiguration.createMailSender(insecure))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rejects_missing_or_non_positive_smtp_timeouts() {
		MailProperties missing = approvedProperties();
		missing.getProperties().remove("mail.smtp.timeout");
		assertThatThrownBy(() -> BrevoSmtpConfiguration.createMailSender(missing))
			.isInstanceOf(IllegalStateException.class);

		MailProperties zero = approvedProperties();
		zero.getProperties().put("mail.smtp.connectiontimeout", "0");
		assertThatThrownBy(() -> BrevoSmtpConfiguration.createMailSender(zero))
			.isInstanceOf(IllegalStateException.class);
	}

	private MailProperties approvedProperties() {
		MailProperties properties = new MailProperties();
		properties.setHost("smtp-relay.brevo.com");
		properties.setPort(587);
		properties.setUsername("b3a5e2001@smtp-brevo.com");
		properties.setPassword("runtime-secret");
		properties.setDefaultEncoding(java.nio.charset.StandardCharsets.UTF_8);
		properties.getProperties().put("mail.smtp.auth", "true");
		properties.getProperties().put("mail.smtp.starttls.enable", "true");
		properties.getProperties().put("mail.smtp.starttls.required", "true");
		properties.getProperties().put("mail.smtp.connectiontimeout", "5000");
		properties.getProperties().put("mail.smtp.timeout", "10000");
		properties.getProperties().put("mail.smtp.writetimeout", "10000");
		return properties;
	}

	private String[] approvedRuntimeProperties() {
		return new String[] {
			"faithlog.auth.email-provider.enabled=true",
			"faithlog.mail.from-name=FaithLog",
			"faithlog.mail.from-email=josephuk77@gmail.com",
			"spring.mail.host=smtp-relay.brevo.com",
			"spring.mail.port=587",
			"spring.mail.username=b3a5e2001@smtp-brevo.com",
			"spring.mail.password=runtime-secret",
			"spring.mail.properties.mail.smtp.auth=true",
			"spring.mail.properties.mail.smtp.starttls.enable=true",
			"spring.mail.properties.mail.smtp.starttls.required=true",
			"spring.mail.properties.mail.smtp.connectiontimeout=5000",
			"spring.mail.properties.mail.smtp.timeout=10000",
			"spring.mail.properties.mail.smtp.writetimeout=10000"
		};
	}
}
