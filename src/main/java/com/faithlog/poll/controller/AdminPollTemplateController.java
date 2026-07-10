package com.faithlog.poll.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.service.PollTemplateService;
import com.faithlog.poll.controller.dto.request.CreatePollTemplateRequest;
import com.faithlog.poll.controller.dto.response.PollTemplateResponse;
import com.faithlog.poll.controller.dto.request.UpdatePollTemplateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campuses/{campusId}/poll-templates")
public class AdminPollTemplateController {

	private final PollTemplateService pollTemplateService;

	public AdminPollTemplateController(PollTemplateService pollTemplateService) {
		this.pollTemplateService = pollTemplateService;
	}

	@GetMapping
	public ApiResponse<List<PollTemplateResponse>> listTemplates(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		List<PollTemplateResponse> responses = pollTemplateService.listTemplates(campusId, authenticatedUser.userId())
			.stream()
			.map(PollTemplateResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}

	@GetMapping("/{templateId}")
	public ApiResponse<PollTemplateResponse> getTemplate(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long templateId
	) {
		return ApiResponse.success(PollTemplateResponse.from(pollTemplateService.getTemplate(campusId, templateId, authenticatedUser.userId())));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<PollTemplateResponse>> createTemplate(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreatePollTemplateRequest request
	) {
		PollTemplateResponse response = PollTemplateResponse.from(pollTemplateService.createTemplate(
			request.toCommand(campusId, authenticatedUser)
		));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(response, "투표 템플릿이 생성되었습니다."));
	}

	@PatchMapping("/{templateId}")
	public ApiResponse<PollTemplateResponse> updateTemplate(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long templateId,
		@Valid @RequestBody UpdatePollTemplateRequest request
	) {
		return ApiResponse.success(PollTemplateResponse.from(pollTemplateService.updateTemplate(
			request.toCommand(campusId, templateId, authenticatedUser)
		)), "투표 템플릿이 수정되었습니다.");
	}

	@DeleteMapping("/{templateId}")
	public ApiResponse<PollTemplateResponse> deactivateTemplate(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long templateId
	) {
		return ApiResponse.success(
			PollTemplateResponse.from(pollTemplateService.deactivateTemplate(campusId, templateId, authenticatedUser.userId())),
			"투표 템플릿이 비활성화되었습니다."
		);
	}
}
