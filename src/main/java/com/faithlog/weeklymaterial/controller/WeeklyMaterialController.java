package com.faithlog.weeklymaterial.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.response.PageResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.weeklymaterial.controller.dto.response.WeeklyMaterialWeekResponse;
import com.faithlog.weeklymaterial.service.WeeklyMaterialQueryService;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/weekly-materials")
public class WeeklyMaterialController {
	private final WeeklyMaterialQueryService queries;

	public WeeklyMaterialController(WeeklyMaterialQueryService queries) {
		this.queries = queries;
	}

	@GetMapping("/current")
	public ApiResponse<WeeklyMaterialWeekResponse> current(@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId) {
		return ApiResponse.success(WeeklyMaterialWeekResponse.from(queries.getCurrent(campusId, user.userId())));
	}

	@GetMapping("/{weekStartDate}")
	public ApiResponse<WeeklyMaterialWeekResponse> week(@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId, @PathVariable LocalDate weekStartDate) {
		return ApiResponse.success(WeeklyMaterialWeekResponse.from(
			queries.getWeek(campusId, user.userId(), weekStartDate)));
	}

	@GetMapping
	public ApiResponse<PageResponse<WeeklyMaterialWeekResponse>> list(@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId, @RequestParam int year, @RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size) {
		var result = queries.list(campusId, user.userId(), year, page, size).map(WeeklyMaterialWeekResponse::from);
		return ApiResponse.success(PageResponse.from(result));
	}
}
