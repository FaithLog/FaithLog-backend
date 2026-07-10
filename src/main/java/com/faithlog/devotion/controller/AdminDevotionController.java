package com.faithlog.devotion.controller;

import com.faithlog.devotion.service.MissingDevotionMemberQueryService;
import com.faithlog.devotion.service.query.GetMissingDevotionMembersQuery;
import com.faithlog.devotion.controller.dto.response.MissingDevotionMemberResponse;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.List;
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

	public AdminDevotionController(MissingDevotionMemberQueryService missingDevotionMemberQueryService) {
		this.missingDevotionMemberQueryService = missingDevotionMemberQueryService;
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
