package com.faithlog.poll.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.application.CreatePollCommentCommand;
import com.faithlog.poll.application.UpdatePollCommentCommand;
import jakarta.validation.constraints.NotBlank;

public record PollCommentRequest(
	@NotBlank String content
) {

	public CreatePollCommentCommand toCreateCommand(Long campusId, Long pollId, AuthenticatedUser authenticatedUser) {
		return new CreatePollCommentCommand(campusId, pollId, authenticatedUser.userId(), content);
	}

	public UpdatePollCommentCommand toUpdateCommand(Long campusId, Long pollId, Long commentId, AuthenticatedUser authenticatedUser) {
		return new UpdatePollCommentCommand(campusId, pollId, commentId, authenticatedUser.userId(), content);
	}
}
