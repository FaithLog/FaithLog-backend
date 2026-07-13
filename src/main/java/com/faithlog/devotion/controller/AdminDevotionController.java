package com.faithlog.devotion.controller;

import com.faithlog.devotion.controller.dto.response.AdminWeeklyDevotionResponse;
import com.faithlog.devotion.controller.dto.response.MissingDevotionMemberResponse;
import com.faithlog.devotion.service.AdminWeeklyDevotionQueryService;
import com.faithlog.devotion.service.MissingDevotionMemberQueryService;
import com.faithlog.devotion.service.export.AdminWeeklyDevotionExcelRenderer;
import com.faithlog.devotion.service.query.GetAdminWeeklyDevotionMembersQuery;
import com.faithlog.devotion.service.query.GetMissingDevotionMembersQuery;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campuses/{campusId}/devotions")
public class AdminDevotionController {

	private final MissingDevotionMemberQueryService missingDevotionMemberQueryService;
	private final AdminWeeklyDevotionQueryService adminWeeklyDevotionQueryService;
	private final AdminWeeklyDevotionExcelRenderer adminWeeklyDevotionExcelRenderer;

	public AdminDevotionController(
		MissingDevotionMemberQueryService missingDevotionMemberQueryService,
		AdminWeeklyDevotionQueryService adminWeeklyDevotionQueryService,
		AdminWeeklyDevotionExcelRenderer adminWeeklyDevotionExcelRenderer
	) {
		this.missingDevotionMemberQueryService = missingDevotionMemberQueryService;
		this.adminWeeklyDevotionQueryService = adminWeeklyDevotionQueryService;
		this.adminWeeklyDevotionExcelRenderer = adminWeeklyDevotionExcelRenderer;
	}

	@GetMapping("/weeks/{weekStartDate}/export")
	public ResponseEntity<byte[]> exportWeeklyMembers(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable LocalDate weekStartDate
	) {
		var result = adminWeeklyDevotionQueryService.getWeeklyMembers(new GetAdminWeeklyDevotionMembersQuery(
			campusId,
			authenticatedUser.userId(),
			weekStartDate
		));
		String filename = "faithlog-devotion-%d-%s.xlsx".formatted(campusId, weekStartDate);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
			))
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
			.body(adminWeeklyDevotionExcelRenderer.render(result));
	}

	@GetMapping("/weeks/{weekStartDate}/members")
	public ApiResponse<AdminWeeklyDevotionResponse> getWeeklyMembers(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable LocalDate weekStartDate
	) {
		return ApiResponse.success(AdminWeeklyDevotionResponse.from(
			adminWeeklyDevotionQueryService.getWeeklyMembers(new GetAdminWeeklyDevotionMembersQuery(
				campusId,
				authenticatedUser.userId(),
				weekStartDate
			))
		));
	}

	@GetMapping("/missing")
	public ApiResponse<List<MissingDevotionMemberResponse>> getMissingMembers(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam LocalDate weekStartDate
	) {
		List<MissingDevotionMemberResponse> responses = missingDevotionMemberQueryService.getMissingMembers(new GetMissingDevotionMembersQuery(
				campusId,
				authenticatedUser.userId(),
				weekStartDate
			))
			.stream()
			.map(MissingDevotionMemberResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}
}
