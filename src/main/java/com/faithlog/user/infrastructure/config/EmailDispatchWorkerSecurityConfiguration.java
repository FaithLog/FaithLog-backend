package com.faithlog.user.infrastructure.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "faithlog.auth.email-dispatch.worker-enabled", havingValue = "true")
public class EmailDispatchWorkerSecurityConfiguration {

	private static final String GOOGLE_ISSUER = "https://accounts.google.com";
	private static final String GOOGLE_JWK_SET = "https://www.googleapis.com/oauth2/v3/certs";

	@Bean
	@Order(1)
	SecurityFilterChain emailDispatchWorkerSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
			.securityMatcher("/internal/v1/email-dispatch/**")
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
			.oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> {
			}))
			.build();
	}

	@Bean
	JwtDecoder emailDispatchWorkerJwtDecoder(
		@Value("${faithlog.auth.email-dispatch.oidc-audience:}") String audience,
		@Value("${faithlog.auth.email-dispatch.oidc-service-account-email:}") String serviceAccountEmail
	) {
		String requiredAudience = requireText(audience, "OIDC audience");
		String requiredServiceAccount = requireText(serviceAccountEmail, "OIDC service account email");
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET).build();
		decoder.setJwtValidator(emailDispatchWorkerJwtValidator(requiredAudience, requiredServiceAccount));
		return decoder;
	}

	OAuth2TokenValidator<Jwt> emailDispatchWorkerJwtValidator(
		String requiredAudience,
		String requiredServiceAccount
	) {
		OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER);
		OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(requiredAudience)
			? OAuth2TokenValidatorResult.success()
			: failure("invalid_audience");
		OAuth2TokenValidator<Jwt> serviceAccountValidator = token -> {
			String email = token.getClaimAsString("email");
			Boolean verified = token.getClaim("email_verified");
			return requiredServiceAccount.equals(email) && Boolean.TRUE.equals(verified)
				? OAuth2TokenValidatorResult.success()
				: failure("invalid_service_account");
		};
		return new DelegatingOAuth2TokenValidator<>(
			List.of(issuer, audienceValidator, serviceAccountValidator)
		);
	}

	private OAuth2TokenValidatorResult failure(String code) {
		return OAuth2TokenValidatorResult.failure(new OAuth2Error(code));
	}

	private String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Email dispatch " + name + " must be configured");
		}
		return value;
	}
}
