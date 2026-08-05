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
	private final PollDocumentAttachmentService documentAttachmentService;

	@Autowired
	PollResultAssembler(
		PollOptionRepository pollOptionRepository,
		PollImageAttachmentService imageAttachmentService,
		PollDocumentAttachmentService documentAttachmentService
	) {
		this.pollOptionRepository = pollOptionRepository;
		this.imageAttachmentService = imageAttachmentService;
		this.documentAttachmentService = documentAttachmentService;
	}

	PollResultAssembler(PollOptionRepository pollOptionRepository) {
		this.pollOptionRepository = pollOptionRepository;
		this.imageAttachmentService = null;
		this.documentAttachmentService = null;
	}

	PollResult toResult(Poll poll) {
		return PollResult.of(
			poll,
			pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id())
				.stream()
				.map(PollOptionResult::from)
				.toList(),
			imageAttachmentService == null ? java.util.List.of()
				: imageAttachmentService.getOrderedAssetIds(poll.id()),
			documentAttachmentService == null ? java.util.List.of()
				: documentAttachmentService.getOrderedAssetIds(poll.id())
		);
	}
}
