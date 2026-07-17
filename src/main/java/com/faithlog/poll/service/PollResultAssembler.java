package com.faithlog.poll.service;

import com.faithlog.poll.service.result.PollOptionResult;
import com.faithlog.poll.service.result.PollResult;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import org.springframework.stereotype.Component;

@Component
class PollResultAssembler {

	private final PollOptionRepository pollOptionRepository;

	PollResultAssembler(PollOptionRepository pollOptionRepository) {
		this.pollOptionRepository = pollOptionRepository;
	}

	PollResult toResult(Poll poll) {
		return PollResult.of(
			poll,
			pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id())
				.stream()
				.map(PollOptionResult::from)
				.toList()
		);
	}
}
