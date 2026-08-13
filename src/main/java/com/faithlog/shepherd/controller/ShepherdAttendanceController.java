package com.faithlog.shepherd.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.shepherd.controller.dto.response.ShepherdHomeCardResponse;
import com.faithlog.shepherd.service.ShepherdService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/shepherd-attendance")
public class ShepherdAttendanceController {

	private final ShepherdService shepherdService;

	public ShepherdAttendanceController(ShepherdService shepherdService) {
		this.shepherdService = shepherdService;
	}

	@GetMapping("/me/home")
	public ApiResponse<ShepherdHomeCardResponse> getMyHome(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		return ApiResponse.success(ShepherdHomeCardResponse.from(
			shepherdService.getMyHome(campusId, authenticatedUser.userId())));
	}
}
