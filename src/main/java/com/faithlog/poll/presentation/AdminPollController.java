package com.faithlog.poll.presentation;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.application.PollService;
import com.faithlog.poll.presentation.dto.CreatePollRequest;
import com.faithlog.poll.presentation.dto.PollResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campuses/{campusId}/polls")
public class AdminPollController {

	private final PollService pollService;

	public AdminPollController(PollService pollService) {
		this.pollService = pollService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<PollResponse>> createPoll(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreatePollRequest request
	) {
		PollResponse response = PollResponse.from(pollService.createPoll(request.toCommand(campusId, authenticatedUser)));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(response, "투표가 생성되었습니다."));
	}
}
