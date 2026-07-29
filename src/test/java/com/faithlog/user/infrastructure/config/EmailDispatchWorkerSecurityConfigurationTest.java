package com.faithlog.user.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class EmailDispatchWorkerSecurityConfigurationTest {

	private static final String AUDIENCE = "https://worker.example.com";
	private static final String SERVICE_ACCOUNT = "tasks@example.iam.gserviceaccount.com";
	private final EmailDispatchWorkerSecurityConfiguration configuration =
		new EmailDispatchWorkerSecurityConfiguration();

	@Test
	void accepts_only_google_oidc_for_the_exact_audience_and_service_account() {
		var validator = configuration.emailDispatchWorkerJwtValidator(AUDIENCE, SERVICE_ACCOUNT);

		assertThat(validator.validate(jwt(
			"https://accounts.google.com",
			List.of(AUDIENCE),
			SERVICE_ACCOUNT,
			true
		)).hasErrors()).isFalse();
		assertThat(validator.validate(jwt(
			"https://accounts.google.com",
			List.of("https://other.example.com"),
			SERVICE_ACCOUNT,
			true
		)).hasErrors()).isTrue();
		assertThat(validator.validate(jwt(
			"https://accounts.google.com",
			List.of(AUDIENCE),
			"other@example.iam.gserviceaccount.com",
			true
		)).hasErrors()).isTrue();
		assertThat(validator.validate(jwt(
			"https://accounts.google.com",
			List.of(AUDIENCE),
			SERVICE_ACCOUNT,
			false
		)).hasErrors()).isTrue();
		assertThat(validator.validate(jwt(
			"https://issuer.example.com",
			List.of(AUDIENCE),
			SERVICE_ACCOUNT,
			true
		)).hasErrors()).isTrue();
	}

	@Test
	void worker_configuration_rejects_missing_oidc_bindings() {
		assertThatThrownBy(() -> configuration.emailDispatchWorkerJwtDecoder("", SERVICE_ACCOUNT))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("audience");
		assertThatThrownBy(() -> configuration.emailDispatchWorkerJwtDecoder(AUDIENCE, ""))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("service account");
	}

	private Jwt jwt(String issuer, List<String> audience, String email, boolean emailVerified) {
		Instant now = Instant.now();
		return Jwt.withTokenValue("token")
			.header("alg", "RS256")
			.issuer(issuer)
			.audience(audience)
			.issuedAt(now.minusSeconds(5))
			.expiresAt(now.plusSeconds(300))
			.claim("email", email)
			.claim("email_verified", emailVerified)
			.build();
	}
}
