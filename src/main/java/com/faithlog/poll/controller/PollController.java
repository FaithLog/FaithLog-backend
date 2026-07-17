package com.faithlog.poll.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.service.command.DeletePollCommentCommand;
import com.faithlog.poll.service.PollCommentCommandService;
import com.faithlog.poll.service.PollCommentQueryService;
import com.faithlog.poll.service.PollQueryService;
import com.faithlog.poll.service.PollResponseCommandService;
import com.faithlog.poll.service.PollResultQueryService;
import com.faithlog.poll.service.PollUserOptionCommandService;
import com.faithlog.poll.controller.dto.request.AddPollOptionRequest;
import com.faithlog.poll.controller.dto.request.PollCommentRequest;
import com.faithlog.poll.controller.dto.response.PollCommentResponse;
import com.faithlog.poll.controller.dto.response.PollDetailResponse;
import com.faithlog.poll.controller.dto.response.PollListResponse;
import com.faithlog.poll.controller.dto.response.PollMyResponseResponse;
import com.faithlog.poll.controller.dto.response.PollOptionResponse;
import com.faithlog.poll.controller.dto.response.PollResultsResponse;
import com.faithlog.poll.controller.dto.request.RespondToPollRequest;
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

	private final PollQueryService pollQueryService;
	private final PollResponseCommandService pollResponseCommandService;
	private final PollResultQueryService pollResultQueryService;
	private final PollCommentCommandService pollCommentCommandService;
	private final PollCommentQueryService pollCommentQueryService;
	private final PollUserOptionCommandService pollUserOptionCommandService;

	public PollController(
		PollQueryService pollQueryService,
		PollResponseCommandService pollResponseCommandService,
		PollResultQueryService pollResultQueryService,
		PollCommentCommandService pollCommentCommandService,
		PollCommentQueryService pollCommentQueryService,
		PollUserOptionCommandService pollUserOptionCommandService
	) {
		this.pollQueryService = pollQueryService;
		this.pollResponseCommandService = pollResponseCommandService;
		this.pollResultQueryService = pollResultQueryService;
		this.pollCommentCommandService = pollCommentCommandService;
		this.pollCommentQueryService = pollCommentQueryService;
		this.pollUserOptionCommandService = pollUserOptionCommandService;
	}

	@GetMapping
	public ApiResponse<List<PollListResponse>> listPolls(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		return ApiResponse.success(pollQueryService.listPolls(campusId, authenticatedUser.userId())
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
		return ApiResponse.success(PollDetailResponse.from(pollQueryService.getPollDetail(campusId, pollId, authenticatedUser.userId())));
	}

	@PutMapping("/{pollId}/responses/me")
	public ApiResponse<PollMyResponseResponse> respondToPoll(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId,
		@Valid @RequestBody RespondToPollRequest request
	) {
		return ApiResponse.success(PollMyResponseResponse.from(pollResponseCommandService.respondToPoll(request.toCommand(campusId, pollId, authenticatedUser))));
	}

	@PostMapping("/{pollId}/options")
	public ResponseEntity<ApiResponse<PollOptionResponse>> addUserOption(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId,
		@Valid @RequestBody AddPollOptionRequest request
	) {
		PollOptionResponse response = PollOptionResponse.from(pollUserOptionCommandService.addUserOption(request.toCommand(campusId, pollId, authenticatedUser)));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(response));
	}

	@GetMapping("/{pollId}/results")
	public ApiResponse<PollResultsResponse> getPollResults(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId
	) {
		return ApiResponse.success(PollResultsResponse.from(pollResultQueryService.getPollResults(campusId, pollId, authenticatedUser.userId())));
	}

	@GetMapping("/{pollId}/comments")
	public ApiResponse<List<PollCommentResponse>> listComments(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId
	) {
		return ApiResponse.success(pollCommentQueryService.listComments(campusId, pollId, authenticatedUser.userId())
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
		PollCommentResponse response = PollCommentResponse.from(pollCommentCommandService.createComment(request.toCreateCommand(campusId, pollId, authenticatedUser)));
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
		return ApiResponse.success(PollCommentResponse.from(pollCommentCommandService.updateComment(request.toUpdateCommand(campusId, pollId, commentId, authenticatedUser))));
	}

	@DeleteMapping("/{pollId}/comments/{commentId}")
	public ResponseEntity<Void> deleteComment(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId,
		@PathVariable Long commentId
	) {
		pollCommentCommandService.deleteComment(new DeletePollCommentCommand(campusId, pollId, commentId, authenticatedUser.userId()));
		return ResponseEntity.noContent().build();
	}
}
