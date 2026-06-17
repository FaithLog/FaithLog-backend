package com.faithlog.user.presentation;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.application.AuthService;
import com.faithlog.user.presentation.dto.UserMeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserMeController {

	private final AuthService authService;

	public UserMeController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping("/me")
	public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		return ApiResponse.success(authService.getCurrentUser(authenticatedUser.userId()));
	}
}
