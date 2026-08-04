package com.faithlog.poll.service;

import com.faithlog.poll.service.result.PollOptionResult;
import com.faithlog.poll.service.result.PollResult;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
class PollResultAssembler {

	private final PollOptionRepository pollOptionRepository;
	private final PollImageAttachmentService imageAttachmentService;

	@Autowired
	PollResultAssembler(PollOptionRepository pollOptionRepository, PollImageAttachmentService imageAttachmentService) {
		this.pollOptionRepository = pollOptionRepository;
		this.imageAttachmentService = imageAttachmentService;
	}

	PollResultAssembler(PollOptionRepository pollOptionRepository) {
		this.pollOptionRepository = pollOptionRepository;
		this.imageAttachmentService = null;
	}

	PollResult toResult(Poll poll) {
		return PollResult.of(
			poll,
			pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id())
				.stream()
				.map(PollOptionResult::from)
				.toList(),
			imageAttachmentService == null ? java.util.List.of()
				: imageAttachmentService.getOrderedAssetIds(poll.id())
		);
	}
}
