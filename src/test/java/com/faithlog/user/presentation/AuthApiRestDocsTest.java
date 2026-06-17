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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.domain.User;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andDo(unauthorizedDocument("users-me-unauthorized"));
	}

	@Test
	void documents_users_me_rejects_refresh_token_bearer() throws Exception {
		String refreshToken = signupAndLogin("docs-refresh-bearer@example.com").refreshToken();

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + refreshToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andDo(document("users-me-refresh-token-unauthorized",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("Authorization").description("Refresh Token을 Bearer로 사용한 잘못된 인증 헤더")
				),
				responseFields(unauthorizedResponseFields())
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
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andDo(document("users-me-inactive-user-unauthorized",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("Authorization").description("비활성 사용자가 발급받았던 Access Token")
				),
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

	private static org.springframework.restdocs.payload.FieldDescriptor[] unauthorizedResponseFields() {
		return new org.springframework.restdocs.payload.FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 실패 응답에서는 `false`"),
			fieldWithPath("code").description("오류 코드. 인증 실패는 `UNAUTHORIZED`"),
			fieldWithPath("message").description("오류 메시지"),
			fieldWithPath("data").optional().description("실패 응답에서는 생략된다"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}
}
