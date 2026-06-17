package com.faithlog.user.presentation;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.user.application.AuthService;
import com.faithlog.user.presentation.dto.LoginRequest;
import com.faithlog.user.presentation.dto.LoginResponse;
import com.faithlog.user.presentation.dto.SignupRequest;
import com.faithlog.user.presentation.dto.SignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
		SignupResponse response = authService.signup(request.toCommand());
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(response, "회원가입이 완료되었습니다."));
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request.toCommand());
		return ApiResponse.success(response, "로그인되었습니다.");
	}
}
