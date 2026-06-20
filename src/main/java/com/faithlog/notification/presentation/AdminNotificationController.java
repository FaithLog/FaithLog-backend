package com.faithlog.notification.presentation;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.notification.application.NotificationLogQueryService;
import com.faithlog.notification.application.NotificationLogSearchCriteria;
import com.faithlog.notification.application.NotificationService;
import com.faithlog.notification.domain.NotificationType;
import com.faithlog.notification.domain.SendStatus;
import com.faithlog.notification.presentation.dto.NotificationLogListResponse;
import com.faithlog.notification.presentation.dto.SendNotificationRequest;
import com.faithlog.notification.presentation.dto.SendNotificationResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/admin/campuses/{campusId}")
public class AdminNotificationController {

	private final NotificationService notificationService;
	private final NotificationLogQueryService notificationLogQueryService;

	public AdminNotificationController(
		NotificationService notificationService,
		NotificationLogQueryService notificationLogQueryService
	) {
		this.notificationService = notificationService;
		this.notificationLogQueryService = notificationLogQueryService;
	}

	@PostMapping("/notifications")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ApiResponse<SendNotificationResponse> sendNotification(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody SendNotificationRequest request
	) {
		return ApiResponse.success(SendNotificationResponse.from(
			notificationService.requestNotification(request.toCommand(campusId, authenticatedUser.userId()))
		));
	}

	@GetMapping("/notification-logs")
	public ApiResponse<NotificationLogListResponse> listNotificationLogs(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam(required = false) NotificationType notificationType,
		@RequestParam(required = false) SendStatus sendStatus,
		@RequestParam(required = false) LocalDate targetWeekStartDate,
		@RequestParam(required = false) Long targetId,
		@RequestParam(required = false) UUID requestId,
		@RequestParam(required = false) LocalDate startDate,
		@RequestParam(required = false) LocalDate endDate,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return ApiResponse.success(NotificationLogListResponse.from(notificationLogQueryService.searchLogs(
			authenticatedUser.userId(),
			new NotificationLogSearchCriteria(
				campusId,
				notificationType,
				sendStatus,
				targetWeekStartDate,
				targetId,
				requestId,
				startDate,
				endDate
			),
			NotificationPageRequests.logs(page, size, sort)
		)));
	}
}
