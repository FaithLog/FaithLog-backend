package com.faithlog.prayer.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.service.PrayerService;
import com.faithlog.prayer.controller.dto.response.PrayerWeekBoardResponse;
import com.faithlog.prayer.controller.dto.request.SaveMyPrayerSubmissionRequest;
import com.faithlog.prayer.controller.dto.request.SavePrayerSubmissionsRequest;
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
@RequestMapping("/api/v1/campuses/{campusId}/prayers")
public class PrayerController {

	private final PrayerService prayerService;

	public PrayerController(PrayerService prayerService) {
		this.prayerService = prayerService;
	}

	@GetMapping("/weeks/{weekStartDate}")
	public ApiResponse<PrayerWeekBoardResponse> getWeeklyBoard(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable LocalDate weekStartDate
	) {
		return ApiResponse.success(PrayerWeekBoardResponse.from(prayerService.getWeeklyBoard(campusId, weekStartDate, authenticatedUser.userId())));
	}

	@PutMapping("/weeks/{weekStartDate}/submissions")
	public ApiResponse<PrayerWeekBoardResponse> saveSubmissions(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable LocalDate weekStartDate,
		@Valid @RequestBody SavePrayerSubmissionsRequest request
	) {
		return ApiResponse.success(PrayerWeekBoardResponse.from(prayerService.saveSubmissions(request.toCommand(campusId, weekStartDate, authenticatedUser))));
	}

	@PutMapping("/weeks/{weekStartDate}/me")
	public ApiResponse<PrayerWeekBoardResponse> saveMySubmission(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable LocalDate weekStartDate,
		@RequestBody SaveMyPrayerSubmissionRequest request
	) {
		return ApiResponse.success(PrayerWeekBoardResponse.from(prayerService.saveMySubmission(request.toCommand(campusId, weekStartDate, authenticatedUser))));
	}
}
