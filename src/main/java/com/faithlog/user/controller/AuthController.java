package com.faithlog.user.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.service.LoginCommandService;
import com.faithlog.user.service.LogoutCommandService;
import com.faithlog.user.service.RefreshTokenRotationService;
import com.faithlog.user.service.SignupCommandService;
import com.faithlog.user.service.result.LoginResult;
import com.faithlog.user.service.result.TokenResult;
import com.faithlog.user.service.result.SignupResult;
import com.faithlog.user.controller.dto.request.LoginRequest;
import com.faithlog.user.controller.dto.response.LoginResponse;
import com.faithlog.user.controller.dto.request.LogoutRequest;
import com.faithlog.user.controller.dto.request.RefreshRequest;
import com.faithlog.user.controller.dto.request.SignupRequest;
import com.faithlog.user.controller.dto.response.SignupResponse;
import com.faithlog.user.controller.dto.response.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final SignupCommandService signupCommandService;
	private final LoginCommandService loginCommandService;
	private final RefreshTokenRotationService refreshTokenRotationService;
	private final LogoutCommandService logoutCommandService;

	public AuthController(
		SignupCommandService signupCommandService,
		LoginCommandService loginCommandService,
		RefreshTokenRotationService refreshTokenRotationService,
		LogoutCommandService logoutCommandService
	) {
		this.signupCommandService = signupCommandService;
		this.loginCommandService = loginCommandService;
		this.refreshTokenRotationService = refreshTokenRotationService;
		this.logoutCommandService = logoutCommandService;
	}

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
		SignupResult result = signupCommandService.signup(request.toCommand());
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(SignupResponse.from(result), "회원가입이 완료되었습니다."));
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResult result = loginCommandService.login(request.toCommand());
		return ApiResponse.success(LoginResponse.from(result), "로그인되었습니다.");
	}

	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		TokenResult result = refreshTokenRotationService.refresh(request.toCommand());
		return ApiResponse.success(TokenResponse.from(result), "토큰이 재발급되었습니다.");
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestBody(required = false) LogoutRequest request
	) {
		logoutCommandService.logout(LogoutRequest.toCommand(request, authenticatedUser));
		return ApiResponse.success(null, "로그아웃되었습니다.");
	}
}
