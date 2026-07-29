package com.faithlog.user.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.user.controller.dto.request.EmailVerificationConfirmRequest;
import com.faithlog.user.controller.dto.request.EmailVerificationRequest;
import com.faithlog.user.controller.dto.request.PasswordResetCompleteRequest;
import com.faithlog.user.controller.dto.response.EmailVerificationRequestResponse;
import com.faithlog.user.controller.dto.response.PasswordResetVerificationResponse;
import com.faithlog.user.controller.dto.response.SignupEmailVerificationResponse;
import com.faithlog.user.service.EmailVerificationCommandService;
import com.faithlog.user.service.PasswordResetCommandService;
import com.faithlog.user.service.result.EmailVerificationRequestResult;
import com.faithlog.user.service.result.EmailVerificationResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class EmailVerificationController {

	private final EmailVerificationCommandService emailVerificationCommandService;
	private final PasswordResetCommandService passwordResetCommandService;

	public EmailVerificationController(
		EmailVerificationCommandService emailVerificationCommandService,
		PasswordResetCommandService passwordResetCommandService
	) {
		this.emailVerificationCommandService = emailVerificationCommandService;
		this.passwordResetCommandService = passwordResetCommandService;
	}

	@PostMapping("/email-verifications/signup/request")
	public ApiResponse<EmailVerificationRequestResponse> requestSignupVerification(
		@Valid @RequestBody EmailVerificationRequest request
	) {
		EmailVerificationRequestResult result = emailVerificationCommandService.requestSignup(request.toCommand());
		return ApiResponse.success(
			EmailVerificationRequestResponse.from(result),
			"인증번호가 발송되었습니다."
		);
	}

	@PostMapping("/email-verifications/signup/confirm")
	public ApiResponse<SignupEmailVerificationResponse> confirmSignupVerification(
		@Valid @RequestBody EmailVerificationConfirmRequest request
	) {
		EmailVerificationResult result = emailVerificationCommandService.confirmSignup(request.toCommand());
		return ApiResponse.success(
			SignupEmailVerificationResponse.from(result),
			"이메일 인증이 완료되었습니다."
		);
	}

	@PostMapping("/password-resets/request")
	public ApiResponse<EmailVerificationRequestResponse> requestPasswordReset(
		@Valid @RequestBody EmailVerificationRequest request
	) {
		EmailVerificationRequestResult result = emailVerificationCommandService.requestPasswordReset(request.toCommand());
		return ApiResponse.success(
			EmailVerificationRequestResponse.from(result),
			"가입된 이메일이라면 인증번호가 발송됩니다."
		);
	}

	@PostMapping("/password-resets/confirm")
	public ApiResponse<PasswordResetVerificationResponse> confirmPasswordReset(
		@Valid @RequestBody EmailVerificationConfirmRequest request
	) {
		EmailVerificationResult result = emailVerificationCommandService.confirmPasswordReset(request.toCommand());
		return ApiResponse.success(
			PasswordResetVerificationResponse.from(result),
			"비밀번호 변경 인증이 완료되었습니다."
		);
	}

	@PostMapping("/password-resets/complete")
	public ApiResponse<Void> completePasswordReset(
		@Valid @RequestBody PasswordResetCompleteRequest request
	) {
		passwordResetCommandService.complete(request.toCommand());
		return ApiResponse.success(null, "비밀번호가 변경되었습니다. 다시 로그인해 주세요.");
	}
}
