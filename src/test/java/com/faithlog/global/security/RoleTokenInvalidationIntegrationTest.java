package com.faithlog.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.campus.domain.CampusRole;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultMatcher;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleTokenInvalidationIntegrationTest {

	@Autowired
	private org.springframework.test.web.servlet.MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private UserRepository userRepository;

	@Test
	void service_role_change_invalidates_old_access_token_and_refresh_issues_latest_version() throws Exception {
		TokenPair adminTokens = signupAndLogin("token-version-admin@example.com", UserRole.ADMIN);
		TokenPair memberTokens = signupAndLogin("token-version-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("token-version-member@example.com").orElseThrow();
		long oldTokenVersion = accessTokenVersion(memberTokens.accessToken());

		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", member.id())
				.header("Authorization", "Bearer " + adminTokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "role": "MANAGER"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.role").value("MANAGER"));

		assertThat(userRepository.findById(member.id()).orElseThrow().tokenVersion())
			.isEqualTo(oldTokenVersion + 1);

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + memberTokens.accessToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		JsonNode refreshResponse = refresh(memberTokens.refreshToken(), status().isOk());
		String refreshedAccessToken = refreshResponse.path("data").path("accessToken").asText();
		Claims refreshedClaims = jwtProvider.parseAccessToken(refreshedAccessToken);

		assertThat(refreshedClaims.get("role", String.class)).isEqualTo("MANAGER");
		assertThat(claimTokenVersion(refreshedClaims)).isEqualTo(oldTokenVersion + 1);

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + refreshedAccessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.role").value("MANAGER"));
	}

	@Test
	void campus_role_change_invalidates_old_access_token_and_refresh_issues_latest_version() throws Exception {
		TokenPair managerTokens = signupAndLogin("token-version-campus-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerTokens.accessToken(), "토큰캠");
		TokenPair memberTokens = signupAndLogin("token-version-campus-member@example.com", UserRole.USER);
		JsonNode membership = joinCampus(memberTokens.accessToken(), campus.path("inviteCode").asText());
		long oldTokenVersion = accessTokenVersion(memberTokens.accessToken());

		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role",
				campus.path("campusId").asLong(),
				membership.path("membershipId").asLong())
				.header("Authorization", "Bearer " + managerTokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "campusRole": "ELDER"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campusRole").value(CampusRole.ELDER.name()));

		User member = userRepository.findByEmail("token-version-campus-member@example.com").orElseThrow();
		assertThat(userRepository.findById(member.id()).orElseThrow().tokenVersion())
			.isEqualTo(oldTokenVersion + 1);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campus.path("campusId").asLong())
				.header("Authorization", "Bearer " + memberTokens.accessToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		JsonNode refreshResponse = refresh(memberTokens.refreshToken(), status().isOk());
		String refreshedAccessToken = refreshResponse.path("data").path("accessToken").asText();
		Claims refreshedClaims = jwtProvider.parseAccessToken(refreshedAccessToken);

		assertThat(claimTokenVersion(refreshedClaims)).isEqualTo(oldTokenVersion + 1);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campus.path("campusId").asLong())
				.header("Authorization", "Bearer " + refreshedAccessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.myCampusRole").value(CampusRole.ELDER.name()));
	}

	@Test
	void logout_blacklist_still_rejects_refreshed_access_token_after_role_invalidation() throws Exception {
		TokenPair adminTokens = signupAndLogin("token-version-logout-admin@example.com", UserRole.ADMIN);
		TokenPair memberTokens = signupAndLogin("token-version-logout-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("token-version-logout-member@example.com").orElseThrow();

		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", member.id())
				.header("Authorization", "Bearer " + adminTokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "role": "MANAGER"
					}
					"""))
			.andExpect(status().isOk());

		JsonNode refreshResponse = refresh(memberTokens.refreshToken(), status().isOk());
		String refreshedAccessToken = refreshResponse.path("data").path("accessToken").asText();
		String refreshedRefreshToken = refreshResponse.path("data").path("refreshToken").asText();

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", "Bearer " + refreshedAccessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + refreshedAccessToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		refresh(refreshedRefreshToken, status().isUnauthorized());
	}

	private long accessTokenVersion(String accessToken) {
		return claimTokenVersion(jwtProvider.parseAccessToken(accessToken));
	}

	private long claimTokenVersion(Claims claims) {
		Number tokenVersion = claims.get("tokenVersion", Number.class);
		assertThat(tokenVersion).isNotNull();
		return tokenVersion.longValue();
	}

	private JsonNode createCampus(String accessToken, String name) throws Exception {
		String body = mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "region": "분당",
					  "description": "분당 %s퍼스"
					}
					""".formatted(name, name)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body).path("data");
	}

	private JsonNode joinCampus(String accessToken, String inviteCode) throws Exception {
		String body = mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "%s"
					}
					""".formatted(inviteCode)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body).path("data");
	}

	private JsonNode refresh(String refreshToken, ResultMatcher statusMatcher) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(refreshToken)))
			.andExpect(statusMatcher)
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body);
	}

	private TokenPair signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "토큰버전",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isCreated());

		User user = userRepository.findByEmail(email).orElseThrow();
		ReflectionTestUtils.setField(user, "role", role);
		userRepository.saveAndFlush(user);

		String loginBody = mockMvc.perform(post("/api/v1/auth/login")
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
		JsonNode response = objectMapper.readTree(loginBody).path("data");
		return new TokenPair(
			response.path("accessToken").asText(),
			response.path("refreshToken").asText()
		);
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}
}
