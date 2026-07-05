package com.faithlog.user.presentation;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.application.port.AccessTokenBlacklistStore;
import com.faithlog.user.application.port.CurrentDeviceFcmTokenDeactivationPort;
import com.faithlog.user.application.port.RefreshTokenStore;
import com.faithlog.user.domain.User;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import com.faithlog.user.support.InMemoryAccessTokenBlacklistStore;
import com.faithlog.user.support.InMemoryRefreshTokenStore;
import com.faithlog.user.support.RecordingCurrentDeviceFcmTokenDeactivationPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@ActiveProfiles("test")
class AuthApiRestDocsTest {

	private static final String PASSWORD = "1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Test
	void documents_signup_success() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "문서회원",
					  "email": "docs-signup@example.com",
					  "password": "1234"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
			.andExpect(jsonPath("$.data.email").value("docs-signup@example.com"))
			.andDo(document("auth-signup-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestFields(
					fieldWithPath("name").description("가입자 이름"),
					fieldWithPath("email").description("로그인에 사용할 이메일"),
					fieldWithPath("password").description("로그인 비밀번호")
				),
				responseFields(apiResponseFields(
					fieldWithPath("data.id").description("생성된 사용자 ID"),
					fieldWithPath("data.name").description("사용자 이름"),
					fieldWithPath("data.email").description("사용자 이메일"),
					fieldWithPath("data.role").description("사용자 전역 역할"),
					fieldWithPath("data.isActive").description("사용자 활성 여부")
				))
			));
	}

	@Test
	void documents_login_success_with_body_tokens() throws Exception {
		signup("docs-login@example.com");

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "docs-login@example.com",
					  "password": "1234"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.accessToken").isString())
			.andExpect(jsonPath("$.data.refreshToken").isString())
			.andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800))
			.andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(1209600))
			.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
			.andDo(document("auth-login-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestFields(
					fieldWithPath("email").description("가입된 사용자 이메일"),
					fieldWithPath("password").description("로그인 비밀번호")
				),
				responseFields(apiResponseFields(
					fieldWithPath("data.user.id").description("사용자 ID"),
					fieldWithPath("data.user.name").description("사용자 이름"),
					fieldWithPath("data.user.email").description("사용자 이메일"),
					fieldWithPath("data.user.role").description("사용자 전역 역할"),
					fieldWithPath("data.user.isActive").description("사용자 활성 여부"),
					fieldWithPath("data.user.lastLoginAt").optional().description("마지막 로그인 시각"),
					fieldWithPath("data.user.campusMemberships").description("캠퍼스 멤버십 목록"),
					fieldWithPath("data.accessToken").description("Authorization Bearer 인증에 사용할 Access Token"),
					fieldWithPath("data.refreshToken").description("모바일 보안 저장소에 보관할 Refresh Token"),
					fieldWithPath("data.accessTokenExpiresIn").description("Access Token 만료 시간(초). 기본값은 1800"),
					fieldWithPath("data.refreshTokenExpiresIn").description("Refresh Token 만료 시간(초). 기본값은 1209600"),
					fieldWithPath("data.tokenType").description("토큰 타입. `Bearer` 고정")
				))
			));
	}

	@Test
	void documents_users_me_success() throws Exception {
		String accessToken = signupAndLogin("docs-me@example.com").accessToken();

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.email").value("docs-me@example.com"))
			.andDo(document("users-me-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("Authorization").description("`Bearer {accessToken}` 형식의 Access Token")
				),
				responseFields(apiResponseFields(
					fieldWithPath("data.id").description("현재 사용자 ID"),
					fieldWithPath("data.name").description("현재 사용자 이름"),
					fieldWithPath("data.email").description("현재 사용자 이메일"),
					fieldWithPath("data.role").description("현재 사용자 전역 역할"),
					fieldWithPath("data.isActive").description("현재 사용자 활성 여부"),
					fieldWithPath("data.lastLoginAt").optional().description("마지막 로그인 시각"),
					fieldWithPath("data.campusMemberships").description("현재 사용자의 캠퍼스 멤버십 목록")
				))
			));
	}

	@Test
	void documents_users_me_unauthorized_without_token() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
			.andDo(unauthorizedDocument("users-me-unauthorized"));
	}

	@Test
	void documents_users_me_rejects_refresh_token_bearer() throws Exception {
		String refreshToken = signupAndLogin("docs-refresh-bearer@example.com").refreshToken();

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + refreshToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
			.andDo(document("users-me-refresh-token-unauthorized",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("Authorization").description("Refresh Token을 Bearer로 사용한 잘못된 인증 헤더")
				),
				responseFields(errorResponseFields())
			));
	}

	@Test
	void documents_users_me_rejects_inactive_user() throws Exception {
		TokenPair tokens = signupAndLogin("docs-inactive@example.com");
		User user = userRepository.findByEmail("docs-inactive@example.com").orElseThrow();
		ReflectionTestUtils.setField(user, "isActive", false);
		userRepository.saveAndFlush(user);

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
			.andDo(document("users-me-inactive-user-unauthorized",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("Authorization").description("비활성 사용자가 발급받았던 Access Token")
				),
				responseFields(errorResponseFields())
			));
	}

	@Test
	void documents_delete_my_account_success() throws Exception {
		TokenPair tokens = signupAndLogin("docs-delete-me@example.com");

		mockMvc.perform(delete("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "password": "1234",
					  "confirmText": "회원탈퇴"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."))
			.andExpect(jsonPath("$.data.deletedAt").exists())
			.andDo(document("users-me-delete-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("Authorization").description("`Bearer {accessToken}` 형식의 Access Token")
				),
				requestFields(
					fieldWithPath("password").description("현재 비밀번호"),
					fieldWithPath("confirmText").description("회원 탈퇴 확인 문구. `회원탈퇴` 고정")
				),
				responseFields(apiResponseFields(
					fieldWithPath("data.deletedAt").description("회원 탈퇴 처리 시각")
				))
			));
	}

	@Test
	void documents_delete_my_account_password_mismatch() throws Exception {
		TokenPair tokens = signupAndLogin("docs-delete-mismatch@example.com");

		mockMvc.perform(delete("/api/v1/users/me")
				.header("Authorization", "Bearer " + tokens.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "password": "wrong",
					  "confirmText": "회원탈퇴"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("USER_DELETE_PASSWORD_MISMATCH"))
			.andDo(document("users-me-delete-password-mismatch",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("Authorization").description("`Bearer {accessToken}` 형식의 Access Token")
				),
				requestFields(
					fieldWithPath("password").description("현재 비밀번호와 다른 값"),
					fieldWithPath("confirmText").description("회원 탈퇴 확인 문구")
				),
				responseFields(unauthorizedResponseFields())
			));
	}

	@Test
	void refresh_and_logout_generate_rest_docs_snippets() throws Exception {
		TokenPair tokens = signupAndLogin("docs-refresh-logout@example.com");

		String refreshBody = mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(tokens.refreshToken())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("토큰이 재발급되었습니다."))
			.andExpect(jsonPath("$.data.accessToken").isString())
			.andExpect(jsonPath("$.data.refreshToken").isString())
			.andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800))
			.andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(1209600))
			.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
			.andDo(document("auth-refresh-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestFields(
					fieldWithPath("refreshToken").description("모바일 보안 저장소에 저장된 Refresh Token")
				),
				responseFields(apiResponseFields(tokenResponseFields()))
			))
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode refreshResponse = objectMapper.readTree(refreshBody);
		String rotatedAccessToken = refreshResponse.path("data").path("accessToken").asText();
		String rotatedRefreshToken = refreshResponse.path("data").path("refreshToken").asText();

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", "Bearer " + rotatedAccessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s",
					  "clientInstanceId": "dummy-client-instance-id",
					  "fcmToken": "dummy-fcm-token"
					}
					""".formatted(rotatedRefreshToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("로그아웃되었습니다."))
			.andDo(document("auth-logout-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("Authorization").description("`Bearer {accessToken}` 형식의 Access Token")
				),
				requestFields(
					fieldWithPath("refreshToken").optional().description("현재 session의 Refresh Token. 없으면 access token 기준으로 로그아웃한다."),
					fieldWithPath("clientInstanceId").optional().description("현재 기기 식별자. 제공되면 현재 사용자 소유 기기 FCM 비활성화 port에 전달한다."),
					fieldWithPath("fcmToken").optional().description("현재 기기 FCM token. 제공되면 현재 사용자 소유 기기 FCM 비활성화 port에 전달한다.")
				),
				responseFields(apiResponseFields())
			));
	}

	@Test
	void documents_refresh_rejects_reused_refresh_token() throws Exception {
		TokenPair tokens = signupAndLogin("docs-refresh-reuse@example.com");

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(tokens.refreshToken())))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(tokens.refreshToken())))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
			.andDo(document("auth-refresh-reused-token-unauthorized",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestFields(
					fieldWithPath("refreshToken").description("이미 rotation으로 폐기된 이전 Refresh Token")
				),
				responseFields(unauthorizedResponseFields())
			));
	}

	@Test
	void documents_logout_unauthorized_without_access_token() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
			.andDo(document("auth-logout-unauthorized",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				responseFields(unauthorizedResponseFields())
			));
	}

	private RestDocumentationResultHandler unauthorizedDocument(String identifier) {
		return document(identifier,
			preprocessRequest(prettyPrint()),
			preprocessResponse(prettyPrint()),
			responseFields(unauthorizedResponseFields())
		);
	}

	private void signup(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "문서회원",
					  "email": "%s",
					  "password": "%s"
					}
					""".formatted(email, PASSWORD)))
			.andExpect(status().isCreated());
	}

	private TokenPair signupAndLogin(String email) throws Exception {
		signup(email);
		ResultActions login = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "%s",
					  "password": "%s"
					}
					""".formatted(email, PASSWORD)))
			.andExpect(status().isOk());

		JsonNode response = objectMapper.readTree(login.andReturn().getResponse().getContentAsString());
		return new TokenPair(
			response.path("data").path("accessToken").asText(),
			response.path("data").path("refreshToken").asText()
		);
	}

	private static org.springframework.restdocs.payload.FieldDescriptor[] apiResponseFields(
		org.springframework.restdocs.payload.FieldDescriptor... dataFields
	) {
		org.springframework.restdocs.payload.FieldDescriptor[] fields =
			new org.springframework.restdocs.payload.FieldDescriptor[5 + dataFields.length];
		fields[0] = fieldWithPath("success").description("요청 성공 여부");
		fields[1] = fieldWithPath("code").description("공통 응답 코드");
		fields[2] = fieldWithPath("message").description("응답 메시지");
		fields[3] = fieldWithPath("data").description("응답 데이터");
		fields[4] = fieldWithPath("timestamp").description("응답 생성 시각");
		System.arraycopy(dataFields, 0, fields, 5, dataFields.length);
		return fields;
	}

	private static org.springframework.restdocs.payload.FieldDescriptor[] tokenResponseFields() {
		return new org.springframework.restdocs.payload.FieldDescriptor[] {
			fieldWithPath("data.accessToken").description("Authorization Bearer 인증에 사용할 새 Access Token"),
			fieldWithPath("data.refreshToken").description("기존 Refresh Token을 대체할 새 Refresh Token"),
			fieldWithPath("data.accessTokenExpiresIn").description("Access Token 만료 시간(초). 기본값은 1800"),
			fieldWithPath("data.refreshTokenExpiresIn").description("Refresh Token 만료 시간(초). 기본값은 1209600"),
			fieldWithPath("data.tokenType").description("토큰 타입. `Bearer` 고정")
		};
	}

	private static org.springframework.restdocs.payload.FieldDescriptor[] unauthorizedResponseFields() {
		return new org.springframework.restdocs.payload.FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 실패 응답에서는 `false`"),
			fieldWithPath("code").description("오류 코드. 인증 실패는 `AUTH_UNAUTHORIZED`"),
			fieldWithPath("message").description("오류 메시지"),
			fieldWithPath("data").optional().description("실패 응답에서는 생략된다"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
	}

	private static org.springframework.restdocs.payload.FieldDescriptor[] errorResponseFields() {
		return new org.springframework.restdocs.payload.FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 실패 응답에서는 `false`"),
			fieldWithPath("code").description("도메인 세부 오류 코드"),
			fieldWithPath("message").description("오류 메시지"),
			fieldWithPath("data").optional().description("실패 응답에서는 생략된다"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}

	@TestConfiguration
	static class TestAuthApiRestDocsPortConfig {

		@Bean
		@Primary
		RefreshTokenStore refreshTokenStore() {
			return new InMemoryRefreshTokenStore();
		}

		@Bean
		@Primary
		AccessTokenBlacklistStore accessTokenBlacklistStore() {
			return new InMemoryAccessTokenBlacklistStore();
		}

		@Bean
		@Primary
		CurrentDeviceFcmTokenDeactivationPort fcmTokenDeactivationPort() {
			return new RecordingCurrentDeviceFcmTokenDeactivationPort();
		}
	}
}
