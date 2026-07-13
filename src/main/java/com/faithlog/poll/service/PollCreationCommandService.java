package com.faithlog.poll.service;

import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.entity.PollTemplate;
import com.faithlog.poll.domain.entity.PollTemplateOption;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollTemplateOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollTemplateRepository;
import com.faithlog.poll.service.command.CreatePollCommand;
import com.faithlog.poll.service.result.PollResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollCreationCommandService {

	private final PollRepository pollRepository;
	private final PollOptionRepository pollOptionRepository;
	private final PollTemplateRepository pollTemplateRepository;
	private final PollTemplateOptionRepository pollTemplateOptionRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final PollOptionSnapshotResolver optionSnapshotResolver;
	private final PollAccessService pollAccessService;
	private final PollStatusSynchronizer pollStatusSynchronizer;
	private final PollResultAssembler pollResultAssembler;

	public PollCreationCommandService(
		PollRepository pollRepository,
		PollOptionRepository pollOptionRepository,
		PollTemplateRepository pollTemplateRepository,
		PollTemplateOptionRepository pollTemplateOptionRepository,
		PaymentAccountRepositoryPort paymentAccountRepository,
		PollOptionSnapshotResolver optionSnapshotResolver,
		PollAccessService pollAccessService,
		PollStatusSynchronizer pollStatusSynchronizer,
		PollResultAssembler pollResultAssembler
	) {
		this.pollRepository = pollRepository;
		this.pollOptionRepository = pollOptionRepository;
		this.pollTemplateRepository = pollTemplateRepository;
		this.pollTemplateOptionRepository = pollTemplateOptionRepository;
		this.paymentAccountRepository = paymentAccountRepository;
		this.optionSnapshotResolver = optionSnapshotResolver;
		this.pollAccessService = pollAccessService;
		this.pollStatusSynchronizer = pollStatusSynchronizer;
		this.pollResultAssembler = pollResultAssembler;
	}

	@Transactional
	public PollResult createPoll(CreatePollCommand command) {
		if (!command.startsAt().isBefore(command.endsAt())) {
			throw new BusinessException(ErrorCode.POLL_INVALID_PERIOD);
		}
		if (command.templateId() != null) {
			return createFromTemplate(command);
		}
		pollAccessService.requirePollCreator(command.campusId(), command.requesterId(), command.pollType());
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
		pollAccessService.requirePollCreator(command.campusId(), command.requesterId(), template.pollType());
		List<PollTemplateOption> templateOptions = pollTemplateOptionRepository.findByTemplateIdOrderBySortOrderAsc(template.id());
		if (templateOptions.isEmpty()) {
			throw new BusinessException(ErrorCode.POLL_INVALID_OPTION);
		}
		requireCoffeePrerequisitesIfNeeded(
			template.pollType(), template.chargeGenerationType(), template.paymentCategory(),
			template.paymentAccountId(), command.campusId(), command.requesterId()
		);
		Poll poll = pollRepository.save(Poll.create(
			command.campusId(), template.id(), command.title(), template.pollType(), template.selectionType(),
			command.isAnonymous(), template.allowUserOptionAdd(), template.chargeGenerationType(),
			template.paymentCategory(), template.paymentAccountId(), command.startsAt(), command.endsAt(),
			command.requesterId()
		));
		pollStatusSynchronizer.openIfCurrent(poll);
		pollOptionRepository.saveAll(templateOptions.stream()
			.map(option -> PollOption.create(
				poll.id(), option.content(), option.composeMenuCode(), option.priceAmount(), option.sortOrder()
			))
			.toList());
		return pollResultAssembler.toResult(poll);
	}

	private PollResult createDirect(CreatePollCommand command) {
		PollType pollType = command.pollType();
		if (pollType == null) {
			throw new BusinessException(ErrorCode.POLL_INVALID_OPTION);
		}
		if (pollType == PollType.MEAL) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED, "MEAL 투표는 밥 투표 API에서만 생성할 수 있습니다.");
		}
		SelectionType selectionType = command.selectionType() == null ? SelectionType.SINGLE : command.selectionType();
		ChargeGenerationType chargeGenerationType = command.chargeGenerationType() == null
			? ChargeGenerationType.NONE
			: command.chargeGenerationType();
		boolean allowUserOptionAdd = command.allowUserOptionAdd() == null
			? pollType == PollType.COFFEE
			: command.allowUserOptionAdd();
		List<PollOptionSnapshot> snapshots = optionSnapshotResolver.resolvePollOptions(pollType, command.options());
		requireCoffeePrerequisitesIfNeeded(
			pollType, chargeGenerationType, command.paymentCategory(), command.paymentAccountId(),
			command.campusId(), command.requesterId()
		);
		Poll poll = pollRepository.save(Poll.create(
			command.campusId(), null, command.title(), pollType, selectionType, command.isAnonymous(),
			allowUserOptionAdd, chargeGenerationType, command.paymentCategory(), command.paymentAccountId(),
			command.startsAt(), command.endsAt(), command.requesterId()
		));
		pollStatusSynchronizer.openIfCurrent(poll);
		pollOptionRepository.saveAll(snapshots.stream()
			.map(snapshot -> PollOption.create(
				poll.id(), snapshot.content(), snapshot.composeMenuCode(), snapshot.priceAmount(), snapshot.sortOrder()
			))
			.toList());
		return pollResultAssembler.toResult(poll);
	}

	private void requireCoffeePrerequisitesIfNeeded(
		PollType pollType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		Long campusId,
		Long requesterId
	) {
		if (pollType != PollType.COFFEE
			&& chargeGenerationType != ChargeGenerationType.OPTION_PRICE
			&& paymentCategory != PaymentCategory.COFFEE) {
			return;
		}
		if (paymentAccountId == null) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
		PaymentAccount account = paymentAccountRepository.findById(paymentAccountId)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING));
		if (!account.isActive()
			|| !account.campusId().equals(campusId)
			|| account.accountType() != PaymentCategory.COFFEE
			|| !requesterId.equals(account.ownerUserId())) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
	}
}
