package com.faithlog.notification.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.notification.controller.dto.response.SendNotificationResponse;
import com.faithlog.notification.service.ChargeReminderService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}")
public class ChargeReminderController {

	private final ChargeReminderService chargeReminderService;

	public ChargeReminderController(ChargeReminderService chargeReminderService) {
		this.chargeReminderService = chargeReminderService;
	}

	@PostMapping("/coffee/charge-reminders")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ApiResponse<SendNotificationResponse> sendCoffeeChargeReminders(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		return ApiResponse.success(SendNotificationResponse.from(
			chargeReminderService.requestCoffeeReminders(campusId, authenticatedUser.userId())
		));
	}

	@PostMapping("/meal/charge-reminders")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ApiResponse<SendNotificationResponse> sendMealChargeReminders(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		return ApiResponse.success(SendNotificationResponse.from(
			chargeReminderService.requestMealReminders(campusId, authenticatedUser.userId())
		));
	}
}
