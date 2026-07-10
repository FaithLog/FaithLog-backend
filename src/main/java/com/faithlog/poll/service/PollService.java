package com.faithlog.poll.service;

import com.faithlog.poll.service.command.AddPollOptionCommand;
import com.faithlog.poll.service.command.CreatePollCommand;
import com.faithlog.poll.service.command.CreatePollCommentCommand;
import com.faithlog.poll.service.command.DeletePollCommentCommand;
import com.faithlog.poll.service.command.RespondToPollCommand;
import com.faithlog.poll.service.command.UpdatePollCommentCommand;
import com.faithlog.poll.service.result.PollCommentResult;
import com.faithlog.poll.service.result.PollDetailResult;
import com.faithlog.poll.service.result.PollListItemResult;
import com.faithlog.poll.service.result.PollMissingMemberResult;
import com.faithlog.poll.service.result.PollOptionResult;
import com.faithlog.poll.service.result.PollResponseResult;
import com.faithlog.poll.service.result.PollResult;
import com.faithlog.poll.service.result.PollResultView;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PollService {

	private final PollCreationCommandService pollCreationCommandService;
	private final PollStatusCommandService pollStatusCommandService;
	private final PollResponseCommandService pollResponseCommandService;
	private final PollQueryService pollQueryService;
	private final PollResultQueryService pollResultQueryService;
	private final PollCommentCommandService pollCommentCommandService;
	private final PollCommentQueryService pollCommentQueryService;
	private final PollUserOptionCommandService pollUserOptionCommandService;

	public PollService(
		PollCreationCommandService pollCreationCommandService,
		PollStatusCommandService pollStatusCommandService,
		PollResponseCommandService pollResponseCommandService,
		PollQueryService pollQueryService,
		PollResultQueryService pollResultQueryService,
		PollCommentCommandService pollCommentCommandService,
		PollCommentQueryService pollCommentQueryService,
		PollUserOptionCommandService pollUserOptionCommandService
	) {
		this.pollCreationCommandService = pollCreationCommandService;
		this.pollStatusCommandService = pollStatusCommandService;
		this.pollResponseCommandService = pollResponseCommandService;
		this.pollQueryService = pollQueryService;
		this.pollResultQueryService = pollResultQueryService;
		this.pollCommentCommandService = pollCommentCommandService;
		this.pollCommentQueryService = pollCommentQueryService;
		this.pollUserOptionCommandService = pollUserOptionCommandService;
	}

	public PollResult createPoll(CreatePollCommand command) {
		return pollCreationCommandService.createPoll(command);
	}

	public List<PollListItemResult> listPolls(Long campusId, Long requesterId) {
		return pollQueryService.listPolls(campusId, requesterId);
	}

	public PollResult getPoll(Long campusId, Long pollId, Long requesterId) {
		return pollQueryService.getPoll(campusId, pollId, requesterId);
	}

	public PollDetailResult getPollDetail(Long campusId, Long pollId, Long requesterId) {
		return pollQueryService.getPollDetail(campusId, pollId, requesterId);
	}

	public PollResult closePoll(Long campusId, Long pollId, Long requesterId) {
		return pollStatusCommandService.closePoll(campusId, pollId, requesterId);
	}

	public PollOptionResult addUserOption(AddPollOptionCommand command) {
		return pollUserOptionCommandService.addUserOption(command);
	}

	public PollResponseResult respondToPoll(RespondToPollCommand command) {
		return pollResponseCommandService.respondToPoll(command);
	}

	public PollResultView getPollResults(Long campusId, Long pollId, Long requesterId) {
		return pollResultQueryService.getPollResults(campusId, pollId, requesterId);
	}

	public List<PollMissingMemberResult> getMissingMembers(Long campusId, Long pollId, Long requesterId) {
		return pollResultQueryService.getMissingMembers(campusId, pollId, requesterId);
	}

	public List<PollCommentResult> listComments(Long campusId, Long pollId, Long requesterId) {
		return pollCommentQueryService.listComments(campusId, pollId, requesterId);
	}

	public PollCommentResult createComment(CreatePollCommentCommand command) {
		return pollCommentCommandService.createComment(command);
	}

	public PollCommentResult updateComment(UpdatePollCommentCommand command) {
		return pollCommentCommandService.updateComment(command);
	}

	public void deleteComment(DeletePollCommentCommand command) {
		pollCommentCommandService.deleteComment(command);
	}
}
