package com.faithlog.poll.presentation;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.application.DeletePollCommentCommand;
import com.faithlog.poll.application.PollService;
import com.faithlog.poll.presentation.dto.AddPollOptionRequest;
import com.faithlog.poll.presentation.dto.PollCommentRequest;
import com.faithlog.poll.presentation.dto.PollCommentResponse;
import com.faithlog.poll.presentation.dto.PollDetailResponse;
import com.faithlog.poll.presentation.dto.PollListResponse;
import com.faithlog.poll.presentation.dto.PollMyResponseResponse;
import com.faithlog.poll.presentation.dto.PollOptionResponse;
import com.faithlog.poll.presentation.dto.PollResultsResponse;
import com.faithlog.poll.presentation.dto.RespondToPollRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/polls")
public class PollController {

	private final PollService pollService;

	public PollController(PollService pollService) {
		this.pollService = pollService;
	}

	@GetMapping
	public ApiResponse<List<PollListResponse>> listPolls(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		return ApiResponse.success(pollService.listPolls(campusId, authenticatedUser.userId())
			.stream()
			.map(PollListResponse::from)
			.toList());
	}

	@GetMapping("/{pollId}")
	public ApiResponse<PollDetailResponse> getPoll(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId
	) {
		return ApiResponse.success(PollDetailResponse.from(pollService.getPollDetail(campusId, pollId, authenticatedUser.userId())));
	}

	@PutMapping("/{pollId}/responses/me")
	public ApiResponse<PollMyResponseResponse> respondToPoll(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId,
		@Valid @RequestBody RespondToPollRequest request
	) {
		return ApiResponse.success(PollMyResponseResponse.from(pollService.respondToPoll(request.toCommand(campusId, pollId, authenticatedUser))));
	}

	@PostMapping("/{pollId}/options")
	public ResponseEntity<ApiResponse<PollOptionResponse>> addUserOption(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId,
		@Valid @RequestBody AddPollOptionRequest request
	) {
		PollOptionResponse response = PollOptionResponse.from(pollService.addUserOption(request.toCommand(campusId, pollId, authenticatedUser)));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(response));
	}

	@GetMapping("/{pollId}/results")
	public ApiResponse<PollResultsResponse> getPollResults(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId
	) {
		return ApiResponse.success(PollResultsResponse.from(pollService.getPollResults(campusId, pollId, authenticatedUser.userId())));
	}

	@GetMapping("/{pollId}/comments")
	public ApiResponse<List<PollCommentResponse>> listComments(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId
	) {
		return ApiResponse.success(pollService.listComments(campusId, pollId, authenticatedUser.userId())
			.stream()
			.map(PollCommentResponse::from)
			.toList());
	}

	@PostMapping("/{pollId}/comments")
	public ResponseEntity<ApiResponse<PollCommentResponse>> createComment(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId,
		@Valid @RequestBody PollCommentRequest request
	) {
		PollCommentResponse response = PollCommentResponse.from(pollService.createComment(request.toCreateCommand(campusId, pollId, authenticatedUser)));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(response));
	}

	@PatchMapping("/{pollId}/comments/{commentId}")
	public ApiResponse<PollCommentResponse> updateComment(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId,
		@PathVariable Long commentId,
		@Valid @RequestBody PollCommentRequest request
	) {
		return ApiResponse.success(PollCommentResponse.from(pollService.updateComment(request.toUpdateCommand(campusId, pollId, commentId, authenticatedUser))));
	}

	@DeleteMapping("/{pollId}/comments/{commentId}")
	public ResponseEntity<Void> deleteComment(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId,
		@PathVariable Long commentId
	) {
		pollService.deleteComment(new DeletePollCommentCommand(campusId, pollId, commentId, authenticatedUser.userId()));
		return ResponseEntity.noContent().build();
	}
}
