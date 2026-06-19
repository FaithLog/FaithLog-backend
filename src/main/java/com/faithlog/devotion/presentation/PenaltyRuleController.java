package com.faithlog.devotion.presentation;

import com.faithlog.devotion.application.PenaltyRuleService;
import com.faithlog.devotion.presentation.dto.PenaltyRuleResponse;
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

	private final PenaltyRuleService penaltyRuleService;

	public PenaltyRuleController(PenaltyRuleService penaltyRuleService) {
		this.penaltyRuleService = penaltyRuleService;
	}

	@GetMapping
	public ApiResponse<List<PenaltyRuleResponse>> listPenaltyRules(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		List<PenaltyRuleResponse> responses = penaltyRuleService.listPenaltyRules(campusId, authenticatedUser.userId())
			.stream()
			.map(PenaltyRuleResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}
}
