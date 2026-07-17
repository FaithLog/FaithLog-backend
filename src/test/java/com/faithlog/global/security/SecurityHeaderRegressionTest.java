package com.faithlog.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeaderRegressionTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String accessToken;

	@BeforeEach
	void createAuthenticatedUser() throws Exception {
		String email = "security-header-" + UUID.randomUUID() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/signup")
				.secure(true)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "보안헤더",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isCreated());

		String loginBody = mockMvc.perform(post("/api/v1/auth/login")
				.secure(true)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode response = objectMapper.readTree(loginBody);
		accessToken = response.path("data").path("accessToken").asText();
	}

	@Test
	void successful_response_keeps_default_security_headers() throws Exception {
		assertDefaultSecurityHeaders(mockMvc.perform(get("/api/v1/health").secure(true)))
			.andExpect(status().isOk());
	}

	@Test
	void unauthenticated_response_keeps_default_security_headers() throws Exception {
		assertDefaultSecurityHeaders(mockMvc.perform(get("/api/v1/users/me").secure(true)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	void forbidden_response_keeps_default_security_headers() throws Exception {
		assertDefaultSecurityHeaders(mockMvc.perform(get("/api/v1/admin/users")
				.secure(true)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_ACCESS_FORBIDDEN"));
	}

	@Test
	void not_found_response_keeps_default_security_headers() throws Exception {
		assertDefaultSecurityHeaders(mockMvc.perform(get("/api/v1/security-header-not-found")
				.secure(true)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)))
			.andExpect(status().isNotFound());
	}

	private ResultActions assertDefaultSecurityHeaders(ResultActions result) throws Exception {
		return result
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"))
			.andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
			.andExpect(header().string(HttpHeaders.EXPIRES, "0"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-XSS-Protection", "0"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
	}
}
