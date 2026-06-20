package com.faithlog.poll.application;

import com.faithlog.billing.application.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.campus.application.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.domain.DutyType;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.domain.PollOption;
import com.faithlog.poll.domain.PollTemplate;
import com.faithlog.poll.domain.PollTemplateOption;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import com.faithlog.poll.infrastructure.jpa.PollOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollService {

	private final PollRepository pollRepository;
	private final PollOptionRepository pollOptionRepository;
	private final PollTemplateRepository pollTemplateRepository;
	private final PollTemplateOptionRepository pollTemplateOptionRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final PollOptionSnapshotResolver optionSnapshotResolver;
	private final PollAccessService pollAccessService;

	public PollService(
		PollRepository pollRepository,
		PollOptionRepository pollOptionRepository,
		PollTemplateRepository pollTemplateRepository,
		PollTemplateOptionRepository pollTemplateOptionRepository,
		PaymentAccountRepositoryPort paymentAccountRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		PollOptionSnapshotResolver optionSnapshotResolver,
		PollAccessService pollAccessService
	) {
		this.pollRepository = pollRepository;
		this.pollOptionRepository = pollOptionRepository;
		this.pollTemplateRepository = pollTemplateRepository;
		this.pollTemplateOptionRepository = pollTemplateOptionRepository;
		this.paymentAccountRepository = paymentAccountRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.optionSnapshotResolver = optionSnapshotResolver;
		this.pollAccessService = pollAccessService;
	}

	@Transactional
	public PollResult createPoll(CreatePollCommand command) {
		pollAccessService.requirePollCreator(command.campusId(), command.requesterId());
		if (!command.startsAt().isBefore(command.endsAt())) {
			throw new BusinessException(ErrorCode.POLL_INVALID_PERIOD);
		}

		if (command.templateId() != null) {
			return createFromTemplate(command);
		}
		return createDirect(command);
	}

	private PollResult createFromTemplate(CreatePollCommand command) {
		PollTemplate template = pollTemplateRepository.findById(command.templateId())
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		if (!template.campusId().equals(command.campusId())) {
			throw new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND);
		}
		if (!template.isActive()) {
			throw new BusinessException(ErrorCode.POLL_TEMPLATE_INACTIVE);
		}
		List<PollTemplateOption> templateOptions = pollTemplateOptionRepository.findByTemplateIdOrderBySortOrderAsc(template.id());
		if (templateOptions.isEmpty()) {
			throw new BusinessException(ErrorCode.POLL_INVALID_OPTION);
		}
		requireCoffeePrerequisitesIfNeeded(template.pollType(), template.chargeGenerationType(), template.paymentCategory(), template.paymentAccountId(), command.campusId());
		Poll poll = pollRepository.save(Poll.create(
			command.campusId(),
			template.id(),
			command.title(),
			template.pollType(),
			template.selectionType(),
			command.isAnonymous(),
			template.chargeGenerationType(),
			template.paymentCategory(),
			template.paymentAccountId(),
			command.startsAt(),
			command.endsAt(),
			command.requesterId()
		));
		pollOptionRepository.saveAll(templateOptions.stream()
			.map(option -> PollOption.create(
				poll.id(),
				option.content(),
				option.composeMenuCode(),
				option.priceAmount(),
				option.sortOrder()
			))
			.toList());
		return toResult(poll);
	}

	private PollResult createDirect(CreatePollCommand command) {
		PollType pollType = command.pollType();
		if (pollType == null) {
			throw new BusinessException(ErrorCode.POLL_INVALID_OPTION);
		}
		SelectionType selectionType = command.selectionType() == null ? SelectionType.SINGLE : command.selectionType();
		ChargeGenerationType chargeGenerationType = command.chargeGenerationType() == null ? ChargeGenerationType.NONE : command.chargeGenerationType();
		List<PollOptionSnapshot> snapshots = optionSnapshotResolver.resolvePollOptions(command.options());
		requireCoffeePrerequisitesIfNeeded(pollType, chargeGenerationType, command.paymentCategory(), command.paymentAccountId(), command.campusId());
		Poll poll = pollRepository.save(Poll.create(
			command.campusId(),
			null,
			command.title(),
			pollType,
			selectionType,
			command.isAnonymous(),
			chargeGenerationType,
			command.paymentCategory(),
			command.paymentAccountId(),
			command.startsAt(),
			command.endsAt(),
			command.requesterId()
		));
		pollOptionRepository.saveAll(snapshots.stream()
			.map(snapshot -> PollOption.create(
				poll.id(),
				snapshot.content(),
				snapshot.composeMenuCode(),
				snapshot.priceAmount(),
				snapshot.sortOrder()
			))
			.toList());
		return toResult(poll);
	}

	private void requireCoffeePrerequisitesIfNeeded(
		PollType pollType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		Long campusId
	) {
		if (pollType == PollType.COFFEE) {
			dutyAssignmentRepository.findByCampusIdAndDutyTypeAndIsActiveTrue(campusId, DutyType.COFFEE)
				.orElseThrow(() -> new BusinessException(ErrorCode.POLL_COFFEE_DUTY_MISSING));
		}
		if (chargeGenerationType != ChargeGenerationType.OPTION_PRICE && paymentCategory != PaymentCategory.COFFEE) {
			return;
		}
		if (paymentAccountId == null) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
		PaymentAccount account = paymentAccountRepository.findById(paymentAccountId)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING));
		if (!account.isActive() || !account.campusId().equals(campusId) || account.accountType() != PaymentCategory.COFFEE) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
	}

	private PollResult toResult(Poll poll) {
		List<PollOptionResult> options = pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id())
			.stream()
			.map(PollOptionResult::from)
			.toList();
		return PollResult.of(poll, options);
	}
}
