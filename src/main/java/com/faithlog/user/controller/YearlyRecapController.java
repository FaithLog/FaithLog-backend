package com.faithlog.user.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.controller.dto.response.YearlyRecapResponse;
import com.faithlog.user.service.YearlyRecapPresentationCommandService;
import com.faithlog.user.service.YearlyRecapQueryService;
import com.faithlog.user.service.result.YearlyRecapResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/yearly-recaps")
public class YearlyRecapController {

	private final YearlyRecapQueryService queryService;
	private final YearlyRecapPresentationCommandService presentationCommandService;

	public YearlyRecapController(
		YearlyRecapQueryService queryService,
		YearlyRecapPresentationCommandService presentationCommandService
	) {
		this.queryService = queryService;
		this.presentationCommandService = presentationCommandService;
	}

	@GetMapping("/previous")
	public ApiResponse<YearlyRecapResponse> getPrevious(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser
	) {
		YearlyRecapResult result = queryService.getPrevious(authenticatedUser.userId());
		return ApiResponse.success(YearlyRecapResponse.from(result));
	}

	@PostMapping("/{recapYear}/presented")
	public ApiResponse<Void> markPresented(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable int recapYear
	) {
		presentationCommandService.markPresented(authenticatedUser.userId(), recapYear);
		return ApiResponse.success(null);
	}
}
