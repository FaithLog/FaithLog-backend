package com.faithlog.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.security.AccessTokenBlacklistChecker;
import com.faithlog.global.security.AccessTokenVersionChecker;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.global.security.SessionRevocationChecker;
import com.faithlog.user.service.EmailVerificationCommandService;
import com.faithlog.user.service.PasswordResetCommandService;
import com.faithlog.user.service.result.EmailVerificationRequestResult;
import com.faithlog.user.service.result.EmailVerificationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmailVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
class EmailVerificationApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EmailVerificationCommandService emailVerificationCommandService;

	@MockitoBean
	private PasswordResetCommandService passwordResetCommandService;

	@MockitoBean private JwtProvider jwtProvider;
	@MockitoBean private AccessTokenBlacklistChecker accessTokenBlacklistChecker;
	@MockitoBean private AccessTokenVersionChecker accessTokenVersionChecker;
	@MockitoBean private SessionRevocationChecker sessionRevocationChecker;

	@Test
	void documents_signup_email_verification_request() throws Exception {
		when(emailVerificationCommandService.requestSignup(any()))
			.thenReturn(new EmailVerificationRequestResult(300, 60));

		mockMvc.perform(post("/api/v1/auth/email-verifications/signup/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"user@example.com\"}"))
			.andExpect(status().isOk())
			.andDo(document("auth-signup-email-verification-request",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestFields(fieldWithPath("email").description("인증할 신규 사용자 이메일")),
				responseFields(successFields(
					fieldWithPath("data.expiresInSeconds").description("인증번호 만료 시간(초)"),
					fieldWithPath("data.resendAvailableInSeconds").description("재전송 가능 대기 시간(초)")
				))
			));
	}

	@Test
	void documents_signup_email_verification_confirm() throws Exception {
		when(emailVerificationCommandService.confirmSignup(any()))
			.thenReturn(new EmailVerificationResult("opaque-signup-token", 600));

		mockMvc.perform(post("/api/v1/auth/email-verifications/signup/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"user@example.com\",\"code\":\"123456\"}"))
			.andExpect(status().isOk())
			.andDo(document("auth-signup-email-verification-confirm",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestFields(
					fieldWithPath("email").description("인증번호를 요청한 이메일"),
					fieldWithPath("code").description("이메일로 받은 숫자 6자리 인증번호")
				),
				responseFields(successFields(
					fieldWithPath("data.emailVerificationToken")
						.description("회원가입 요청에 한 번만 사용할 opaque token"),
					fieldWithPath("data.expiresInSeconds").description("token 만료 시간(초)")
				))
			));
	}

	@Test
	void documents_password_reset_request_and_confirm() throws Exception {
		when(emailVerificationCommandService.requestPasswordReset(any()))
			.thenReturn(new EmailVerificationRequestResult(300, 60));
		when(emailVerificationCommandService.confirmPasswordReset(any()))
			.thenReturn(new EmailVerificationResult("opaque-reset-token", 600));

		mockMvc.perform(post("/api/v1/auth/password-resets/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"user@example.com\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("가입된 이메일이라면 인증번호가 발송됩니다."))
			.andDo(document("auth-password-reset-request",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestFields(fieldWithPath("email").description("가입 여부를 확인할 수 없게 처리되는 이메일")),
				responseFields(successFields(
					fieldWithPath("data.expiresInSeconds").description("인증번호 만료 시간(초)"),
					fieldWithPath("data.resendAvailableInSeconds").description("재전송 가능 대기 시간(초)")
				))
			));

		mockMvc.perform(post("/api/v1/auth/password-resets/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"user@example.com\",\"code\":\"123456\"}"))
			.andExpect(status().isOk())
			.andDo(document("auth-password-reset-confirm",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestFields(
					fieldWithPath("email").description("인증번호를 요청한 이메일"),
					fieldWithPath("code").description("이메일로 받은 숫자 6자리 인증번호")
				),
				responseFields(successFields(
					fieldWithPath("data.passwordResetToken")
						.description("비밀번호 변경 요청에 한 번만 사용할 opaque token"),
					fieldWithPath("data.expiresInSeconds").description("token 만료 시간(초)")
				))
			));
	}

	@Test
	void documents_password_reset_completion_and_same_password_error() throws Exception {
		mockMvc.perform(post("/api/v1/auth/password-resets/complete")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resetToken\":\"opaque-reset-token\",\"newPassword\":\"new-password\"}"))
			.andExpect(status().isOk())
			.andDo(document("auth-password-reset-complete",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestFields(
					fieldWithPath("resetToken").description("확인 API에서 받은 일회용 passwordResetToken"),
					fieldWithPath("newPassword").description("사용자가 직접 입력한 새 비밀번호")
				),
				responseFields(successWithoutDataFields())
			));

		doThrow(new BusinessException(ErrorCode.AUTH_PASSWORD_RESET_SAME_PASSWORD))
			.when(passwordResetCommandService)
			.complete(any());
		mockMvc.perform(post("/api/v1/auth/password-resets/complete")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resetToken\":\"another-token\",\"newPassword\":\"same-password\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_PASSWORD_RESET_SAME_PASSWORD"))
			.andDo(document("auth-password-reset-same-password",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestFields(
					fieldWithPath("resetToken").description("확인 API에서 받은 일회용 passwordResetToken"),
					fieldWithPath("newPassword").description("현재 비밀번호와 동일해 거부되는 값")
				),
				responseFields(errorFields())
			));
	}

	private static org.springframework.restdocs.payload.FieldDescriptor[] successFields(
		org.springframework.restdocs.payload.FieldDescriptor... dataFields
	) {
		var fields = new org.springframework.restdocs.payload.FieldDescriptor[5 + dataFields.length];
		fields[0] = fieldWithPath("success").description("요청 성공 여부");
		fields[1] = fieldWithPath("code").description("공통 응답 코드");
		fields[2] = fieldWithPath("message").description("응답 메시지");
		fields[3] = fieldWithPath("data").description("응답 데이터");
		fields[4] = fieldWithPath("timestamp").description("응답 생성 시각");
		System.arraycopy(dataFields, 0, fields, 5, dataFields.length);
		return fields;
	}

	private static org.springframework.restdocs.payload.FieldDescriptor[] successWithoutDataFields() {
		return new org.springframework.restdocs.payload.FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부"),
			fieldWithPath("code").description("공통 응답 코드"),
			fieldWithPath("message").description("재로그인이 필요하다는 성공 메시지"),
			fieldWithPath("data").optional().description("자동 로그인 token을 반환하지 않아 생략된다"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
	}

	private static org.springframework.restdocs.payload.FieldDescriptor[] errorFields() {
		return new org.springframework.restdocs.payload.FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 실패에서는 false"),
			fieldWithPath("code").description("전용 오류 코드"),
			fieldWithPath("message").description("안전한 오류 메시지"),
			fieldWithPath("data").optional().description("실패 응답에서는 생략된다"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
	}
}
