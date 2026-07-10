package com.faithlog.devotion.controller;

import com.faithlog.devotion.service.PenaltyRuleQueryService;
import com.faithlog.devotion.controller.dto.response.PenaltyRuleResponse;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/penalty-rules")
public class PenaltyRuleController {

	private final PenaltyRuleQueryService penaltyRuleQueryService;

	public PenaltyRuleController(PenaltyRuleQueryService penaltyRuleQueryService) {
		this.penaltyRuleQueryService = penaltyRuleQueryService;
	}

	@GetMapping
	public ApiResponse<List<PenaltyRuleResponse>> listPenaltyRules(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		List<PenaltyRuleResponse> responses = penaltyRuleQueryService.listPenaltyRules(campusId, authenticatedUser.userId())
			.stream()
			.map(PenaltyRuleResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}
}
