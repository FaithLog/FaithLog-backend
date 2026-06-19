package com.faithlog.devotion.presentation;

import com.faithlog.devotion.application.PenaltyRuleResult;
import com.faithlog.devotion.application.PenaltyRuleService;
import com.faithlog.devotion.presentation.dto.CreatePenaltyRuleRequest;
import com.faithlog.devotion.presentation.dto.PenaltyRuleResponse;
import com.faithlog.devotion.presentation.dto.UpdatePenaltyRuleRequest;
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
@RequestMapping("/api/v1/admin")
public class AdminPenaltyRuleController {

	private final PenaltyRuleService penaltyRuleService;

	public AdminPenaltyRuleController(PenaltyRuleService penaltyRuleService) {
		this.penaltyRuleService = penaltyRuleService;
	}

	@PostMapping("/campuses/{campusId}/penalty-rules")
	public ResponseEntity<ApiResponse<PenaltyRuleResponse>> createPenaltyRule(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreatePenaltyRuleRequest request
	) {
		PenaltyRuleResult result = penaltyRuleService.createPenaltyRule(request.toCommand(campusId, authenticatedUser));
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(PenaltyRuleResponse.from(result), "벌금 규칙이 등록되었습니다."));
	}

	@PatchMapping("/penalty-rules/{ruleId}")
	public ApiResponse<PenaltyRuleResponse> updatePenaltyRule(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long ruleId,
		@Valid @RequestBody UpdatePenaltyRuleRequest request
	) {
		PenaltyRuleResult result = penaltyRuleService.updatePenaltyRule(request.toCommand(ruleId, authenticatedUser));
		return ApiResponse.success(PenaltyRuleResponse.from(result), "벌금 규칙이 수정되었습니다.");
	}
}
