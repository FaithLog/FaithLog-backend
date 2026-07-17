package com.faithlog.poll.service;

import com.faithlog.poll.domain.entity.PollTemplateOption;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollTemplateOptionRepository;
import com.faithlog.poll.service.command.CreatePollTemplateOptionCommand;
import com.faithlog.poll.service.result.PollTemplateOptionResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class PollTemplateOptionSupport {

	private final PollOptionSnapshotResolver optionSnapshotResolver;
	private final PollTemplateOptionRepository pollTemplateOptionRepository;

	PollTemplateOptionSupport(
		PollOptionSnapshotResolver optionSnapshotResolver,
		PollTemplateOptionRepository pollTemplateOptionRepository
	) {
		this.optionSnapshotResolver = optionSnapshotResolver;
		this.pollTemplateOptionRepository = pollTemplateOptionRepository;
	}

	List<PollOptionSnapshot> resolve(PollType pollType, List<CreatePollTemplateOptionCommand> commands) {
		return optionSnapshotResolver.resolveTemplateOptions(pollType, commands);
	}

	void save(Long templateId, List<PollOptionSnapshot> snapshots) {
		pollTemplateOptionRepository.saveAll(snapshots.stream()
			.map(snapshot -> PollTemplateOption.create(
				templateId,
				snapshot.content(),
				snapshot.composeMenuCode(),
				snapshot.priceAmount(),
				snapshot.sortOrder()
			))
			.toList());
	}

	void replace(Long templateId, List<PollOptionSnapshot> snapshots) {
		pollTemplateOptionRepository.deleteByTemplateId(templateId);
		save(templateId, snapshots);
	}

	List<PollTemplateOptionResult> results(Long templateId) {
		return pollTemplateOptionRepository.findByTemplateIdOrderBySortOrderAsc(templateId)
			.stream()
			.map(PollTemplateOptionResult::from)
			.toList();
	}
}
