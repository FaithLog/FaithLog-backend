package com.faithlog.user.presentation;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.application.AuthService;
import com.faithlog.user.application.DeleteMyAccountResult;
import com.faithlog.user.application.UserMeResult;
import com.faithlog.user.application.UserAccountService;
import com.faithlog.user.presentation.dto.DeleteMyAccountRequest;
import com.faithlog.user.presentation.dto.DeleteMyAccountResponse;
import com.faithlog.user.presentation.dto.UserMeResponse;
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
