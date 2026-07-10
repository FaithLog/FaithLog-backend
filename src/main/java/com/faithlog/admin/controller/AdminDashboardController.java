package com.faithlog.admin.controller;

import com.faithlog.admin.service.AdminDashboardQueryService;
import com.faithlog.admin.controller.dto.response.AdminDashboardSummaryResponse;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campuses/{campusId}/dashboard")
public class AdminDashboardController {

	private final AdminDashboardQueryService adminDashboardQueryService;

	public AdminDashboardController(AdminDashboardQueryService adminDashboardQueryService) {
		this.adminDashboardQueryService = adminDashboardQueryService;
	}

	@GetMapping("/summary")
	public ApiResponse<AdminDashboardSummaryResponse> getSummary(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam(required = false) LocalDate weekStartDate
	) {
		return ApiResponse.success(AdminDashboardSummaryResponse.from(
			adminDashboardQueryService.getSummary(campusId, authenticatedUser.userId(), weekStartDate)
		));
	}
}
