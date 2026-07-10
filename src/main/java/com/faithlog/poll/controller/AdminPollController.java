package com.faithlog.poll.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.service.PollCreationCommandService;
import com.faithlog.poll.service.PollResultQueryService;
import com.faithlog.poll.service.PollStatusCommandService;
import com.faithlog.poll.controller.dto.request.CreatePollRequest;
import com.faithlog.poll.controller.dto.response.PollMissingMemberResponse;
import com.faithlog.poll.controller.dto.response.PollResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campuses/{campusId}/polls")
public class AdminPollController {

	private final PollCreationCommandService pollCreationCommandService;
	private final PollResultQueryService pollResultQueryService;
	private final PollStatusCommandService pollStatusCommandService;

	public AdminPollController(
		PollCreationCommandService pollCreationCommandService,
		PollResultQueryService pollResultQueryService,
		PollStatusCommandService pollStatusCommandService
	) {
		this.pollCreationCommandService = pollCreationCommandService;
		this.pollResultQueryService = pollResultQueryService;
		this.pollStatusCommandService = pollStatusCommandService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<PollResponse>> createPoll(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreatePollRequest request
	) {
		PollResponse response = PollResponse.from(pollCreationCommandService.createPoll(request.toCommand(campusId, authenticatedUser)));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(response, "투표가 생성되었습니다."));
	}

	@GetMapping("/{pollId}/missing-members")
	public ApiResponse<List<PollMissingMemberResponse>> getMissingMembers(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId
	) {
		return ApiResponse.success(pollResultQueryService.getMissingMembers(campusId, pollId, authenticatedUser.userId())
			.stream()
			.map(PollMissingMemberResponse::from)
			.toList());
	}

	@PatchMapping("/{pollId}/close")
	public ApiResponse<PollResponse> closePoll(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId
	) {
		return ApiResponse.success(PollResponse.from(pollStatusCommandService.closePoll(campusId, pollId, authenticatedUser.userId())));
	}
}
