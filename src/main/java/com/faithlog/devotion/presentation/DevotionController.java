package com.faithlog.devotion.presentation;

import com.faithlog.devotion.application.DevotionService;
import com.faithlog.devotion.application.GetMyWeeklyDevotionQuery;
import com.faithlog.devotion.presentation.dto.DailyDevotionResponse;
import com.faithlog.devotion.presentation.dto.UpdateDailyDevotionRequest;
import com.faithlog.devotion.presentation.dto.UpdateWeeklyDevotionRequest;
import com.faithlog.devotion.presentation.dto.WeeklyDevotionResponse;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/devotions/me")
public class DevotionController {

	private final DevotionService devotionService;

	public DevotionController(DevotionService devotionService) {
		this.devotionService = devotionService;
	}

	@PutMapping("/days/{recordDate}")
	public ApiResponse<DailyDevotionResponse> updateDailyCheck(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable LocalDate recordDate,
		@Valid @RequestBody UpdateDailyDevotionRequest request
	) {
		return ApiResponse.success(DailyDevotionResponse.from(
			devotionService.updateDailyCheck(request.toCommand(campusId, authenticatedUser, recordDate))
		));
	}

	@PutMapping("/weeks/{weekStartDate}")
	public ApiResponse<WeeklyDevotionResponse> updateWeeklyCheck(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable LocalDate weekStartDate,
		@Valid @RequestBody UpdateWeeklyDevotionRequest request
	) {
		return ApiResponse.success(WeeklyDevotionResponse.from(
			devotionService.updateWeeklyCheck(request.toCommand(campusId, authenticatedUser, weekStartDate))
		));
	}

	@GetMapping("/weeks/{weekStartDate}")
	public ApiResponse<WeeklyDevotionResponse> getMyWeeklyCheck(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable LocalDate weekStartDate
	) {
		return ApiResponse.success(WeeklyDevotionResponse.from(
			devotionService.getMyWeeklyCheck(new GetMyWeeklyDevotionQuery(
				campusId,
				authenticatedUser.userId(),
				weekStartDate
			))
		));
	}
}
