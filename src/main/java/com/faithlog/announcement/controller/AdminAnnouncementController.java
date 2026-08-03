package com.faithlog.announcement.controller;

import com.faithlog.announcement.controller.dto.request.CreateAnnouncementCategoryRequest;
import com.faithlog.announcement.controller.dto.request.CreateAnnouncementRequest;
import com.faithlog.announcement.controller.dto.request.UpdateAnnouncementCategoryRequest;
import com.faithlog.announcement.controller.dto.request.UpdateAnnouncementRequest;
import com.faithlog.announcement.controller.dto.response.AnnouncementCategoryResponse;
import com.faithlog.announcement.controller.dto.response.AnnouncementResponse;
import com.faithlog.announcement.service.AnnouncementCategoryCommandService;
import com.faithlog.announcement.service.AnnouncementCommandService;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campuses/{campusId}")
public class AdminAnnouncementController {

	private final AnnouncementCategoryCommandService categoryCommandService;
	private final AnnouncementCommandService announcementCommandService;

	public AdminAnnouncementController(
		AnnouncementCategoryCommandService categoryCommandService,
		AnnouncementCommandService announcementCommandService
	) {
		this.categoryCommandService = categoryCommandService;
		this.announcementCommandService = announcementCommandService;
	}

	@PostMapping("/announcement-categories")
	public ResponseEntity<ApiResponse<AnnouncementCategoryResponse>> createCategory(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId,
		@Valid @RequestBody CreateAnnouncementCategoryRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(AnnouncementCategoryResponse.from(
			categoryCommandService.createCategory(request.toCommand(campusId, user.userId())))));
	}

	@PatchMapping("/announcement-categories/{categoryId}")
	public ApiResponse<AnnouncementCategoryResponse> updateCategory(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId,
		@PathVariable Long categoryId,
		@Valid @RequestBody UpdateAnnouncementCategoryRequest request
	) {
		return ApiResponse.success(AnnouncementCategoryResponse.from(
			categoryCommandService.updateCategory(request.toCommand(campusId, categoryId, user.userId()))));
	}

	@PostMapping("/announcement-categories/{categoryId}/deactivate")
	public ResponseEntity<Void> deactivateCategory(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId,
		@PathVariable Long categoryId
	) {
		categoryCommandService.deactivateCategory(campusId, categoryId, user.userId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/announcements")
	public ResponseEntity<ApiResponse<AnnouncementResponse>> createAnnouncement(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId,
		@Valid @RequestBody CreateAnnouncementRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(AnnouncementResponse.from(
			announcementCommandService.createAnnouncement(request.toCommand(campusId, user.userId())))));
	}

	@PatchMapping("/announcements/{announcementId}")
	public ApiResponse<AnnouncementResponse> updateAnnouncement(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId,
		@PathVariable Long announcementId,
		@Valid @RequestBody UpdateAnnouncementRequest request
	) {
		return ApiResponse.success(AnnouncementResponse.from(announcementCommandService.updateAnnouncement(
			request.toCommand(campusId, announcementId, user.userId()))));
	}

	@PostMapping("/announcements/{announcementId}/publish")
	public ApiResponse<AnnouncementResponse> publishAnnouncement(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId,
		@PathVariable Long announcementId
	) {
		return ApiResponse.success(AnnouncementResponse.from(
			announcementCommandService.publishAnnouncement(campusId, announcementId, user.userId())));
	}

	@PostMapping("/announcements/{announcementId}/archive")
	public ResponseEntity<Void> archiveAnnouncement(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId,
		@PathVariable Long announcementId
	) {
		announcementCommandService.archiveAnnouncement(campusId, announcementId, user.userId());
		return ResponseEntity.noContent().build();
	}
}
