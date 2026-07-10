package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.service.command.AddPollOptionCommand;
import com.faithlog.poll.service.result.PollOptionResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollUserOptionCommandService {

	private final PollOptionRepository pollOptionRepository;
	private final PollOptionSnapshotResolver optionSnapshotResolver;
	private final PollAccessService pollAccessService;
	private final PollLookupPolicy pollLookupPolicy;
	private final PollStatusSynchronizer pollStatusSynchronizer;

	public PollUserOptionCommandService(
		PollOptionRepository pollOptionRepository,
		PollOptionSnapshotResolver optionSnapshotResolver,
		PollAccessService pollAccessService,
		PollLookupPolicy pollLookupPolicy,
		PollStatusSynchronizer pollStatusSynchronizer
	) {
		this.pollOptionRepository = pollOptionRepository;
		this.optionSnapshotResolver = optionSnapshotResolver;
		this.pollAccessService = pollAccessService;
		this.pollLookupPolicy = pollLookupPolicy;
		this.pollStatusSynchronizer = pollStatusSynchronizer;
	}

	@Transactional
	public PollOptionResult addUserOption(AddPollOptionCommand command) {
		pollAccessService.requireActiveCampusMember(command.campusId(), command.requesterId());
		Poll poll = pollLookupPolicy.getPollInCampus(command.campusId(), command.pollId());
		pollStatusSynchronizer.requireOpenPoll(poll);
		if (!poll.allowUserOptionAdd()) {
			throw new BusinessException(ErrorCode.POLL_USER_OPTION_ADD_DISABLED);
		}
		List<PollOption> options = pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id());
		int nextSortOrder = options.stream().mapToInt(PollOption::sortOrder).max().orElse(0) + 1;
		PollOptionSnapshot snapshot = optionSnapshotResolver.resolveUserAddedOption(
			poll.pollType(), command.content(), command.menuId(), nextSortOrder
		);
		boolean duplicated = options.stream()
			.map(PollOption::content)
			.anyMatch(existingContent -> existingContent.equalsIgnoreCase(snapshot.content()));
		if (duplicated) {
			throw new BusinessException(ErrorCode.POLL_OPTION_DUPLICATE_CONTENT);
		}
		return PollOptionResult.from(pollOptionRepository.save(PollOption.createUserAdded(
			poll.id(), snapshot.content(), snapshot.composeMenuCode(), snapshot.priceAmount(),
			snapshot.sortOrder(), command.requesterId()
		)));
	}
}
