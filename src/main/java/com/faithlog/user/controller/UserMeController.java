package com.faithlog.user.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.service.AccountWithdrawalCommandService;
import com.faithlog.user.service.UserMeQueryService;
import com.faithlog.user.service.result.DeleteMyAccountResult;
import com.faithlog.user.service.result.UserMeResult;
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

	private final UserMeQueryService userMeQueryService;
	private final AccountWithdrawalCommandService accountWithdrawalCommandService;

	public UserMeController(
		UserMeQueryService userMeQueryService,
		AccountWithdrawalCommandService accountWithdrawalCommandService
	) {
		this.userMeQueryService = userMeQueryService;
		this.accountWithdrawalCommandService = accountWithdrawalCommandService;
	}

	@GetMapping("/me")
	public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		UserMeResult result = userMeQueryService.getCurrentUser(authenticatedUser.userId());
		return ApiResponse.success(UserMeResponse.from(result));
	}

	@DeleteMapping("/me")
	public ApiResponse<DeleteMyAccountResponse> deleteMe(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@Valid @RequestBody DeleteMyAccountRequest request
	) {
		DeleteMyAccountResult result = accountWithdrawalCommandService.deleteMyAccount(request.toCommand(authenticatedUser));
		return ApiResponse.success(DeleteMyAccountResponse.from(result), "회원 탈퇴가 완료되었습니다.");
	}
}
