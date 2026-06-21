package com.faithlog.admin.presentation;

import com.faithlog.admin.application.AdminDashboardService;
import com.faithlog.admin.presentation.dto.AdminDashboardSummaryResponse;
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

	private final AdminDashboardService adminDashboardService;

	public AdminDashboardController(AdminDashboardService adminDashboardService) {
		this.adminDashboardService = adminDashboardService;
	}

	@GetMapping("/summary")
	public ApiResponse<AdminDashboardSummaryResponse> getSummary(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam(required = false) LocalDate weekStartDate
	) {
		return ApiResponse.success(AdminDashboardSummaryResponse.from(
			adminDashboardService.getSummary(campusId, authenticatedUser.userId(), weekStartDate)
		));
	}
}
