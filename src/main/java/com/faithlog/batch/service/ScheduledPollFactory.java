package com.faithlog.batch.service;

import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.entity.PollTemplate;
import com.faithlog.poll.domain.entity.PollTemplateOption;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollTemplateOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollTemplateRepository;
import com.faithlog.poll.service.CoffeeOperationClassifier;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class ScheduledPollFactory {

	private final PollTemplateRepository pollTemplateRepository;
	private final PollTemplateOptionRepository pollTemplateOptionRepository;
	private final PollRepository pollRepository;
	private final PollOptionRepository pollOptionRepository;

	ScheduledPollFactory(
		PollTemplateRepository pollTemplateRepository,
		PollTemplateOptionRepository pollTemplateOptionRepository,
		PollRepository pollRepository,
		PollOptionRepository pollOptionRepository
	) {
		this.pollTemplateRepository = pollTemplateRepository;
		this.pollTemplateOptionRepository = pollTemplateOptionRepository;
		this.pollRepository = pollRepository;
		this.pollOptionRepository = pollOptionRepository;
	}

	boolean createIfAbsent(Long templateId, ScheduledPollWindow window) {
		PollTemplate template = pollTemplateRepository.findById(templateId).orElseThrow();
		if (CoffeeOperationClassifier.isCoffeeOperation(
			template.pollType(), template.chargeGenerationType(), template.paymentCategory())) {
			return false;
		}
		boolean exists = pollRepository.existsByCampusIdAndTemplateIdAndStartsAtGreaterThanEqualAndStartsAtLessThan(
			template.campusId(),
			template.id(),
			window.weekStartInstant(),
			window.nextWeekStartInstant()
		);
		if (exists) {
			return false;
		}
		List<PollTemplateOption> templateOptions = pollTemplateOptionRepository.findByTemplateIdOrderBySortOrderAsc(template.id());
		if (templateOptions.isEmpty()) {
			return false;
		}

		Poll poll = Poll.create(
			template.campusId(),
			template.id(),
			template.title(),
			template.pollType(),
			template.selectionType(),
			false,
			template.allowUserOptionAdd(),
			template.chargeGenerationType(),
			template.paymentCategory(),
			template.paymentAccountId(),
			window.startsAt(),
			window.endsAt(),
			null
		);
		poll.open();
		Poll savedPoll = pollRepository.save(poll);
		pollOptionRepository.saveAll(templateOptions.stream()
			.map(option -> PollOption.create(
				savedPoll.id(),
				option.content(),
				option.composeMenuCode(),
				option.priceAmount(),
				option.sortOrder()
			))
			.toList());
		return true;
	}
}
