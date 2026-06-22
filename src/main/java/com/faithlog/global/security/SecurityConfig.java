package com.faithlog.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

	public SecurityConfig(
		JwtAuthenticationFilter jwtAuthenticationFilter,
		RestAuthenticationEntryPoint restAuthenticationEntryPoint
	) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
		return http
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exception -> exception.authenticationEntryPoint(restAuthenticationEntryPoint))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(paths.matcher(HttpMethod.GET, "/api/v1/health")).permitAll()
				.requestMatchers(
					paths.matcher(HttpMethod.POST, "/api/v1/auth/signup"),
					paths.matcher(HttpMethod.POST, "/api/v1/auth/login"),
					paths.matcher(HttpMethod.POST, "/api/v1/auth/refresh")
				).permitAll()
				.requestMatchers(
					paths.matcher("/actuator/health"),
					paths.matcher("/swagger-ui.html"),
					paths.matcher("/swagger-ui/**"),
					paths.matcher("/api-docs/**")
				).permitAll()
				.anyRequest().authenticated())
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
