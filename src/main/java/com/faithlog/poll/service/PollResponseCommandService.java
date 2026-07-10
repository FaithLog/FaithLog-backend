package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.domain.entity.PollResponseOption;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.poll.service.command.RespondToPollCommand;
import com.faithlog.poll.service.result.PollResponseResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollResponseCommandService {

	private final PollOptionRepository pollOptionRepository;
	private final PollResponseRepository pollResponseRepository;
	private final PollResponseOptionRepository pollResponseOptionRepository;
	private final PollAccessService pollAccessService;
	private final PollLookupSupport pollLookupSupport;
	private final PollStatusSynchronizer pollStatusSynchronizer;

	public PollResponseCommandService(
		PollOptionRepository pollOptionRepository,
		PollResponseRepository pollResponseRepository,
		PollResponseOptionRepository pollResponseOptionRepository,
		PollAccessService pollAccessService,
		PollLookupSupport pollLookupSupport,
		PollStatusSynchronizer pollStatusSynchronizer
	) {
		this.pollOptionRepository = pollOptionRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.pollResponseOptionRepository = pollResponseOptionRepository;
		this.pollAccessService = pollAccessService;
		this.pollLookupSupport = pollLookupSupport;
		this.pollStatusSynchronizer = pollStatusSynchronizer;
	}

	@Transactional
	public PollResponseResult respondToPoll(RespondToPollCommand command) {
		pollAccessService.requireActiveCampusMember(command.campusId(), command.requesterId());
		Poll poll = pollLookupSupport.getPollInCampus(command.campusId(), command.pollId());
		pollStatusSynchronizer.requireOpenPoll(poll);
		validateSelectionCount(poll.selectionType(), command.optionIds());
		validateNoDuplicateOptions(command.optionIds());
		Map<Long, PollOption> optionsById = optionsById(command.pollId());
		for (Long optionId : command.optionIds()) {
			if (!optionsById.containsKey(optionId)) {
				throw new BusinessException(ErrorCode.POLL_OPTION_NOT_FOUND);
			}
		}

		PollResponse response = pollResponseRepository.findByPollIdAndUserId(poll.id(), command.requesterId())
			.orElseGet(() -> pollResponseRepository.save(
				PollResponse.create(poll.id(), command.requesterId(), command.memo())
			));
		response.updateMemo(command.memo());
		pollResponseOptionRepository.deleteByResponseId(response.id());
		pollResponseOptionRepository.saveAll(command.optionIds().stream()
			.map(optionId -> PollResponseOption.create(response.id(), optionId))
			.toList());
		return PollResponseResult.of(response, command.optionIds());
	}

	private void validateSelectionCount(SelectionType selectionType, List<Long> optionIds) {
		if (optionIds == null || optionIds.isEmpty()) {
			throw new BusinessException(ErrorCode.POLL_RESPONSE_INVALID_SELECTION_COUNT);
		}
		if (selectionType == SelectionType.SINGLE && optionIds.size() != 1) {
			throw new BusinessException(ErrorCode.POLL_RESPONSE_INVALID_SELECTION_COUNT);
		}
	}

	private void validateNoDuplicateOptions(List<Long> optionIds) {
		if (new HashSet<>(optionIds).size() != optionIds.size()) {
			throw new BusinessException(ErrorCode.POLL_RESPONSE_DUPLICATE_OPTION);
		}
	}

	private Map<Long, PollOption> optionsById(Long pollId) {
		Map<Long, PollOption> optionsById = new HashMap<>();
		pollOptionRepository.findByPollIdOrderBySortOrderAsc(pollId)
			.forEach(option -> optionsById.put(option.id(), option));
		return optionsById;
	}
}
