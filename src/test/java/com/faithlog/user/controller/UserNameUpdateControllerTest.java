package com.faithlog.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserNameUpdateControllerTest {

	private static final String PASSWORD = "1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Test
	void updates_only_my_name_and_returns_full_user_me_response_with_active_memberships() throws Exception {
		TokenPair tokens = signupAndLogin("name-update-success@example.com", "변경 전 이름");
		User before = userRepository.findByEmail("name-update-success@example.com").orElseThrow();
		Campus campus = campusRepository.saveAndFlush(Campus.create(
			"이름수정캠퍼스", "서울", "이름 수정 응답 멤버십", "NAME-UPDATE-CAMPUS"
		));
		CampusMember membership = campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), before.id()));
		UnchangedUserFields unchanged = UnchangedUserFields.from(before);

		mockMvc.perform(patch("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "새 이름"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.data.id").value(before.id()))
			.andExpect(jsonPath("$.data.name").value("새 이름"))
			.andExpect(jsonPath("$.data.email").value(before.email()))
			.andExpect(jsonPath("$.data.role").value(before.role().name()))
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andExpect(jsonPath("$.data.lastLoginAt").exists())
			.andExpect(jsonPath("$.data.campusMemberships.length()").value(1))
			.andExpect(jsonPath("$.data.campusMemberships[0].campusMemberId").value(membership.id()))
			.andExpect(jsonPath("$.data.campusMemberships[0].campusId").value(campus.id()))
			.andExpect(jsonPath("$.data.campusMemberships[0].campusName").value("이름수정캠퍼스"))
			.andExpect(jsonPath("$.data.campusMemberships[0].campusRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.campusMemberships[0].status").value("ACTIVE"));

		User after = userRepository.findById(before.id()).orElseThrow();
		assertThat(after.name()).isEqualTo("새 이름");
		unchanged.assertPreservedBy(after);
	}

	@Test
	void trims_name_at_the_request_dto_boundary() throws Exception {
		TokenPair tokens = signupAndLogin("name-update-trim@example.com", "변경 전 이름");

		mockMvc.perform(patch("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "   공백 제거 이름   "
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("공백 제거 이름"));

		assertThat(userRepository.findByEmail("name-update-trim@example.com").orElseThrow().name())
			.isEqualTo("공백 제거 이름");
	}

	@Test
	void accepts_name_at_the_100_character_boundary() throws Exception {
		TokenPair tokens = signupAndLogin("name-update-max-length@example.com", "변경 전 이름");
		String maxLengthName = "가".repeat(100);

		mockMvc.perform(patch("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"%s\"}".formatted(maxLengthName)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value(maxLengthName));

		assertThat(userRepository.findByEmail("name-update-max-length@example.com").orElseThrow().name())
			.isEqualTo(maxLengthName);
	}

	@Test
	void same_name_is_an_idempotent_success_without_changing_other_fields() throws Exception {
		TokenPair tokens = signupAndLogin("name-update-idempotent@example.com", "같은 이름");
		User before = userRepository.findByEmail("name-update-idempotent@example.com").orElseThrow();
		UnchangedUserFields unchanged = UnchangedUserFields.from(before);

		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(patch("/api/v1/users/me")
					.header("Authorization", "Bearer " + tokens.accessToken())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
						  "name": "같은 이름"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("같은 이름"));
		}

		User after = userRepository.findById(before.id()).orElseThrow();
		assertThat(after.name()).isEqualTo("같은 이름");
		unchanged.assertPreservedBy(after);
	}

	@ParameterizedTest(name = "invalid request {index}: {0}")
	@MethodSource("invalidNameRequests")
	void rejects_invalid_names_with_validation_400_and_preserves_database(String requestBody) throws Exception {
		String email = "name-update-validation-" + Math.abs(requestBody.hashCode()) + "@example.com";
		TokenPair tokens = signupAndLogin(email, "검증 전 이름");
		User before = userRepository.findByEmail(email).orElseThrow();
		UnchangedUserFields unchanged = UnchangedUserFields.from(before);

		mockMvc.perform(patch("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("GLOBAL_VALIDATION_FAILED"));

		User after = userRepository.findById(before.id()).orElseThrow();
		assertThat(after.name()).isEqualTo("검증 전 이름");
		unchanged.assertPreservedBy(after);
	}

	@Test
	void rejects_missing_access_token_and_preserves_database() throws Exception {
		signup("name-update-no-token@example.com", "인증 전 이름");

		mockMvc.perform(patch("/api/v1/users/me")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"변경 시도\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		assertThat(userRepository.findByEmail("name-update-no-token@example.com").orElseThrow().name())
			.isEqualTo("인증 전 이름");
	}

	@Test
	void rejects_refresh_token_bearer_and_preserves_database() throws Exception {
		TokenPair tokens = signupAndLogin("name-update-refresh@example.com", "인증 전 이름");

		mockMvc.perform(patch("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.refreshToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"변경 시도\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		assertThat(userRepository.findByEmail("name-update-refresh@example.com").orElseThrow().name())
			.isEqualTo("인증 전 이름");
	}

	@Test
	void rejects_inactive_user_and_preserves_database() throws Exception {
		TokenPair tokens = signupAndLogin("name-update-inactive@example.com", "인증 전 이름");
		User user = userRepository.findByEmail("name-update-inactive@example.com").orElseThrow();
		ReflectionTestUtils.setField(user, "isActive", false);
		userRepository.saveAndFlush(user);

		mockMvc.perform(patch("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"변경 시도\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		User after = userRepository.findById(user.id()).orElseThrow();
		assertThat(after.name()).isEqualTo("인증 전 이름");
		assertThat(after.isActive()).isFalse();
	}

	private static Stream<Arguments> invalidNameRequests() {
		return Stream.of(
			Arguments.of("{\"name\":null}"),
			Arguments.of("{\"name\":\"\"}"),
			Arguments.of("{\"name\":\"   \"}"),
			Arguments.of("{\"name\":\"%s\"}".formatted("가".repeat(101)))
		);
	}

	private TokenPair signupAndLogin(String email, String name) throws Exception {
		signup(email, name);
		String responseBody = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "%s",
					  "password": "%s"
					}
					""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode data = objectMapper.readTree(responseBody).path("data");
		return new TokenPair(data.path("accessToken").asText(), data.path("refreshToken").asText());
	}

	private void signup(String email, String name) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "email": "%s",
					  "password": "%s"
					}
					""".formatted(name, email, PASSWORD)))
			.andExpect(status().isCreated());
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}

	private record UnchangedUserFields(
		String email,
		String passwordHash,
		UserRole role,
		boolean isActive,
		long tokenVersion
	) {

		static UnchangedUserFields from(User user) {
			return new UnchangedUserFields(
				user.email(), user.passwordHash(), user.role(), user.isActive(), user.tokenVersion()
			);
		}

		void assertPreservedBy(User user) {
			assertThat(user.email()).isEqualTo(email);
			assertThat(user.passwordHash()).isEqualTo(passwordHash);
			assertThat(user.role()).isEqualTo(role);
			assertThat(user.isActive()).isEqualTo(isActive);
			assertThat(user.tokenVersion()).isEqualTo(tokenVersion);
		}
	}
}
