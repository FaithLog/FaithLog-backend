package com.faithlog.announcement.controller;

import com.faithlog.announcement.controller.dto.response.AnnouncementCategoryResponse;
import com.faithlog.announcement.controller.dto.response.AnnouncementResponse;
import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.service.AnnouncementCategoryQueryService;
import com.faithlog.announcement.service.AnnouncementQueryService;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.response.PageResponse;
import com.faithlog.global.security.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}")
public class AnnouncementController {

	private final AnnouncementCategoryQueryService categoryQueryService;
	private final AnnouncementQueryService announcementQueryService;

	public AnnouncementController(
		AnnouncementCategoryQueryService categoryQueryService,
		AnnouncementQueryService announcementQueryService
	) {
		this.categoryQueryService = categoryQueryService;
		this.announcementQueryService = announcementQueryService;
	}

	@GetMapping("/announcement-categories")
	public ApiResponse<List<AnnouncementCategoryResponse>> getCategories(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId
	) {
		return ApiResponse.success(categoryQueryService.getCategories(campusId, user.userId()).stream()
			.map(AnnouncementCategoryResponse::from).toList());
	}

	@GetMapping("/announcements")
	public ApiResponse<PageResponse<AnnouncementResponse>> getAnnouncements(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId,
		@RequestParam(defaultValue = "PUBLISHED") AnnouncementStatus status,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {
		var result = announcementQueryService.getAnnouncements(
			campusId, user.userId(), status, AnnouncementPageRequests.stable(page, size)).map(AnnouncementResponse::from);
		return ApiResponse.success(PageResponse.from(result));
	}

	@GetMapping("/announcements/{announcementId}")
	public ApiResponse<AnnouncementResponse> getAnnouncement(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId,
		@PathVariable Long announcementId
	) {
		return ApiResponse.success(AnnouncementResponse.from(
			announcementQueryService.getAnnouncement(campusId, announcementId, user.userId())));
	}
}
