package com.faithlog.user.presentation;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.application.AuthService;
import com.faithlog.user.application.LoginResult;
import com.faithlog.user.application.TokenResult;
import com.faithlog.user.application.SignupResult;
import com.faithlog.user.presentation.dto.LoginRequest;
import com.faithlog.user.presentation.dto.LoginResponse;
import com.faithlog.user.presentation.dto.LogoutRequest;
import com.faithlog.user.presentation.dto.RefreshRequest;
import com.faithlog.user.presentation.dto.SignupRequest;
import com.faithlog.user.presentation.dto.SignupResponse;
import com.faithlog.user.presentation.dto.TokenResponse;
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

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
		SignupResult result = authService.signup(request.toCommand());
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(SignupResponse.from(result), "회원가입이 완료되었습니다."));
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResult result = authService.login(request.toCommand());
		return ApiResponse.success(LoginResponse.from(result), "로그인되었습니다.");
	}

	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		TokenResult result = authService.refresh(request.toCommand());
		return ApiResponse.success(TokenResponse.from(result), "토큰이 재발급되었습니다.");
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestBody(required = false) LogoutRequest request
	) {
		authService.logout(LogoutRequest.toCommand(request, authenticatedUser));
		return ApiResponse.success(null, "로그아웃되었습니다.");
	}
}
