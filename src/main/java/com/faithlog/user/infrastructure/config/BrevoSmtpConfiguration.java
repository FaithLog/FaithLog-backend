package com.faithlog.user.infrastructure.config;

import com.faithlog.user.infrastructure.email.BrevoSmtpEmailSenderAdapter;
import com.faithlog.user.service.port.EmailSenderPort;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@ConditionalOnProperty(name = "faithlog.auth.email-provider.enabled", havingValue = "true")
@EnableConfigurationProperties(MailProperties.class)
public class BrevoSmtpConfiguration {

	static final String BREVO_HOST = "smtp-relay.brevo.com";
	static final int BREVO_PORT = 587;
	static final String BREVO_USERNAME = "b3a5e2001@smtp-brevo.com";
	static final String LOGO_SHA256 = "6059e8f38377cfe485752cdf2fa37a014e01586246de5fd4ff26a40446d1b748";
	private static final Map<String, String> REQUIRED_SMTP_PROPERTIES = Map.of(
		"mail.smtp.auth", "true",
		"mail.smtp.starttls.enable", "true",
		"mail.smtp.starttls.required", "true"
	);
	private static final String[] TIMEOUT_PROPERTIES = {
		"mail.smtp.connectiontimeout",
		"mail.smtp.timeout",
		"mail.smtp.writetimeout"
	};

	@Bean
	JavaMailSender brevoJavaMailSender(MailProperties properties) {
		return createMailSender(properties);
	}

	@Bean
	EmailSenderPort brevoEmailSenderPort(
		JavaMailSender brevoJavaMailSender,
		@Value("${faithlog.mail.from-name:}") String senderName,
		@Value("${faithlog.mail.from-email:}") String senderEmail,
		@Value("classpath:/mail/faithlog-logo.png") Resource logoResource
	) {
		return new BrevoSmtpEmailSenderAdapter(
			brevoJavaMailSender,
			senderName,
			senderEmail,
			logoResource,
			LOGO_SHA256
		);
	}

	static JavaMailSenderImpl createMailSender(MailProperties properties) {
		if (properties == null
			|| !BREVO_HOST.equals(properties.getHost())
			|| properties.getPort() == null
			|| properties.getPort() != BREVO_PORT
			|| !BREVO_USERNAME.equals(properties.getUsername())
			|| properties.getPassword() == null
			|| properties.getPassword().isBlank()) {
			throw new IllegalStateException("Brevo SMTP configuration is invalid");
		}

		Map<String, String> configured = properties.getProperties();
		REQUIRED_SMTP_PROPERTIES.forEach((key, value) -> {
			if (!value.equals(configured.get(key))) {
				throw new IllegalStateException("Brevo SMTP security configuration is invalid");
			}
		});
		for (String key : TIMEOUT_PROPERTIES) {
			requirePositiveInteger(configured.get(key));
		}

		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(properties.getHost());
		sender.setPort(properties.getPort());
		sender.setUsername(properties.getUsername());
		sender.setPassword(properties.getPassword());
		sender.setDefaultEncoding(properties.getDefaultEncoding().name());
		Properties javaMailProperties = new Properties();
		javaMailProperties.putAll(configured);
		sender.setJavaMailProperties(javaMailProperties);
		return sender;
	}

	private static void requirePositiveInteger(String value) {
		try {
			if (value == null || Integer.parseInt(value) <= 0) {
				throw new IllegalStateException("Brevo SMTP timeout configuration is invalid");
			}
		} catch (NumberFormatException exception) {
			throw new IllegalStateException("Brevo SMTP timeout configuration is invalid");
		}
	}
}
