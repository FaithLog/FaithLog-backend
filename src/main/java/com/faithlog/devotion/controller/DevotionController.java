package com.faithlog.devotion.controller;

import com.faithlog.devotion.service.DailyDevotionCommandService;
import com.faithlog.devotion.service.DevotionMonthlySummaryQueryService;
import com.faithlog.devotion.service.MyWeeklyDevotionQueryService;
import com.faithlog.devotion.service.WeeklyDevotionCommandService;
import com.faithlog.devotion.service.query.GetMyMonthlyDevotionSummaryQuery;
import com.faithlog.devotion.service.query.GetMyWeeklyDevotionQuery;
import com.faithlog.devotion.controller.dto.response.DailyDevotionResponse;
import com.faithlog.devotion.controller.dto.response.MyMonthlyDevotionSummaryResponse;
import com.faithlog.devotion.controller.dto.request.UpdateDailyDevotionRequest;
import com.faithlog.devotion.controller.dto.request.UpdateWeeklyDevotionRequest;
import com.faithlog.devotion.controller.dto.response.WeeklyDevotionResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/devotions/me")
public class DevotionController {

	private final DailyDevotionCommandService dailyDevotionCommandService;
	private final WeeklyDevotionCommandService weeklyDevotionCommandService;
	private final MyWeeklyDevotionQueryService myWeeklyDevotionQueryService;
	private final DevotionMonthlySummaryQueryService monthlySummaryQueryService;

	public DevotionController(
		DailyDevotionCommandService dailyDevotionCommandService,
		WeeklyDevotionCommandService weeklyDevotionCommandService,
		MyWeeklyDevotionQueryService myWeeklyDevotionQueryService,
		DevotionMonthlySummaryQueryService monthlySummaryQueryService
	) {
		this.dailyDevotionCommandService = dailyDevotionCommandService;
		this.weeklyDevotionCommandService = weeklyDevotionCommandService;
		this.myWeeklyDevotionQueryService = myWeeklyDevotionQueryService;
		this.monthlySummaryQueryService = monthlySummaryQueryService;
	}

	@PutMapping("/days/{recordDate}")
	public ApiResponse<DailyDevotionResponse> updateDailyCheck(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable LocalDate recordDate,
		@Valid @RequestBody UpdateDailyDevotionRequest request
	) {
		return ApiResponse.success(DailyDevotionResponse.from(
			dailyDevotionCommandService.updateDailyCheck(request.toCommand(campusId, authenticatedUser, recordDate))
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
			weeklyDevotionCommandService.updateWeeklyCheck(request.toCommand(campusId, authenticatedUser, weekStartDate))
		));
	}

	@GetMapping("/weeks/{weekStartDate}")
	public ApiResponse<WeeklyDevotionResponse> getMyWeeklyCheck(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable LocalDate weekStartDate
	) {
		return ApiResponse.success(WeeklyDevotionResponse.from(
			myWeeklyDevotionQueryService.getMyWeeklyCheck(new GetMyWeeklyDevotionQuery(
				campusId,
				authenticatedUser.userId(),
				weekStartDate
			))
		));
	}

	@GetMapping("/monthly-summary")
	public ApiResponse<MyMonthlyDevotionSummaryResponse> getMyMonthlySummary(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam int year,
		@RequestParam int month
	) {
		return ApiResponse.success(MyMonthlyDevotionSummaryResponse.from(
			monthlySummaryQueryService.getMyMonthlySummary(new GetMyMonthlyDevotionSummaryQuery(
				campusId,
				authenticatedUser.userId(),
				year,
				month
			))
		));
	}
}
