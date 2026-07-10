package com.faithlog.user.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.service.AuthService;
import com.faithlog.user.service.result.DeleteMyAccountResult;
import com.faithlog.user.service.result.UserMeResult;
import com.faithlog.user.service.UserAccountService;
import com.faithlog.user.controller.dto.request.DeleteMyAccountRequest;
import com.faithlog.user.controller.dto.response.DeleteMyAccountResponse;
import com.faithlog.user.controller.dto.response.UserMeResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserMeController {

	private final AuthService authService;
	private final UserAccountService userAccountService;

	public UserMeController(AuthService authService, UserAccountService userAccountService) {
		this.authService = authService;
		this.userAccountService = userAccountService;
	}

	@GetMapping("/me")
	public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		UserMeResult result = authService.getCurrentUser(authenticatedUser.userId());
		return ApiResponse.success(UserMeResponse.from(result));
	}

	@DeleteMapping("/me")
	public ApiResponse<DeleteMyAccountResponse> deleteMe(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@Valid @RequestBody DeleteMyAccountRequest request
	) {
		DeleteMyAccountResult result = userAccountService.deleteMyAccount(request.toCommand(authenticatedUser));
		return ApiResponse.success(DeleteMyAccountResponse.from(result), "회원 탈퇴가 완료되었습니다.");
	}
}
