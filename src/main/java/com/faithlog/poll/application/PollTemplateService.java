package com.faithlog.poll.application;

import com.faithlog.billing.application.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.PollTemplate;
import com.faithlog.poll.domain.PollTemplateOption;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.infrastructure.jpa.PollTemplateOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollTemplateService {

	private final PollTemplateRepository pollTemplateRepository;
	private final PollTemplateOptionRepository pollTemplateOptionRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final PollOptionSnapshotResolver optionSnapshotResolver;
	private final PollAccessService pollAccessService;

	public PollTemplateService(
		PollTemplateRepository pollTemplateRepository,
		PollTemplateOptionRepository pollTemplateOptionRepository,
		PaymentAccountRepositoryPort paymentAccountRepository,
		PollOptionSnapshotResolver optionSnapshotResolver,
		PollAccessService pollAccessService
	) {
		this.pollTemplateRepository = pollTemplateRepository;
		this.pollTemplateOptionRepository = pollTemplateOptionRepository;
		this.paymentAccountRepository = paymentAccountRepository;
		this.optionSnapshotResolver = optionSnapshotResolver;
		this.pollAccessService = pollAccessService;
	}

	@Transactional
	public PollTemplateResult createTemplate(CreatePollTemplateCommand command) {
		requireTemplateManageAccess(
			command.campusId(),
			command.requesterId(),
			command.pollType(),
			command.chargeGenerationType(),
			command.paymentCategory()
		);
		requirePaymentAccountIfNeeded(
			command.chargeGenerationType(),
			command.paymentCategory(),
			command.paymentAccountId(),
			command.campusId(),
			command.requesterId()
		);
		List<PollOptionSnapshot> snapshots = optionSnapshotResolver.resolveTemplateOptions(command.options());
		PollTemplate template = pollTemplateRepository.save(PollTemplate.create(
			command.campusId(),
			command.title(),
			command.pollType(),
			command.selectionType(),
			command.chargeGenerationType(),
			command.paymentCategory(),
			command.paymentAccountId(),
			command.allowUserOptionAdd(),
			command.autoCreateEnabled(),
			command.startDayOfWeek(),
			command.startTime(),
			command.endDayOfWeek(),
			command.endTime(),
			false
		));
		saveOptions(template.id(), snapshots);
		return toResult(template);
	}

	@Transactional
	public PollTemplateResult updateTemplate(UpdatePollTemplateCommand command) {
		PollTemplate template = pollTemplateRepository.findById(command.templateId())
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		requireSameCampusScope(template, command.campusId());
		requireTemplateManageAccess(
			command.campusId(),
			command.requesterId(),
			template.pollType(),
			command.chargeGenerationType(),
			command.paymentCategory()
		);
		requirePaymentAccountIfNeeded(
			command.chargeGenerationType(),
			command.paymentCategory(),
			command.paymentAccountId(),
			command.campusId(),
			command.requesterId()
		);
		List<PollOptionSnapshot> snapshots = optionSnapshotResolver.resolveTemplateOptions(command.options());
		template.update(
			command.title(),
			command.selectionType(),
			command.chargeGenerationType(),
			command.paymentCategory(),
			command.paymentAccountId(),
			command.allowUserOptionAdd(),
			command.autoCreateEnabled(),
			command.startDayOfWeek(),
			command.startTime(),
			command.endDayOfWeek(),
			command.endTime()
		);
		pollTemplateOptionRepository.deleteByTemplateId(template.id());
		saveOptions(template.id(), snapshots);
		return toResult(template);
	}

	@Transactional
	public PollTemplateResult deactivateTemplate(Long campusId, Long templateId, Long requesterId) {
		PollTemplate template = pollTemplateRepository.findById(templateId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		requireSameCampusScope(template, campusId);
		pollAccessService.requireTemplateManager(campusId, requesterId);
		template.deactivate();
		return toResult(template);
	}

	@Transactional(readOnly = true)
	public List<PollTemplateResult> listTemplates(Long campusId, Long requesterId) {
		pollAccessService.requireTemplateManager(campusId, requesterId);
		return pollTemplateRepository.findByCampusIdAndIsActiveTrueOrderByIdAsc(campusId)
			.stream()
			.map(this::toResult)
			.toList();
	}

	@Transactional(readOnly = true)
	public PollTemplateResult getTemplate(Long campusId, Long templateId, Long requesterId) {
		PollTemplate template = pollTemplateRepository.findById(templateId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		requireSameCampusScope(template, campusId);
		pollAccessService.requireTemplateManager(campusId, requesterId);
		return toResult(template);
	}

	private void requireSameCampusScope(PollTemplate template, Long campusId) {
		if (!template.campusId().equals(campusId)) {
			throw new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND);
		}
	}

	private void saveOptions(Long templateId, List<PollOptionSnapshot> snapshots) {
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

	private PollTemplateResult toResult(PollTemplate template) {
		List<PollTemplateOptionResult> options = pollTemplateOptionRepository.findByTemplateIdOrderBySortOrderAsc(template.id())
			.stream()
			.map(PollTemplateOptionResult::from)
			.toList();
		return PollTemplateResult.of(template, options);
	}

	private void requirePaymentAccountIfNeeded(
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		Long campusId,
		Long requesterId
	) {
		if (chargeGenerationType != ChargeGenerationType.OPTION_PRICE && paymentCategory != PaymentCategory.COFFEE) {
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
			|| (account.ownerUserId() != null && !account.ownerUserId().equals(requesterId))) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
	}

	private void requireTemplateManageAccess(
		Long campusId,
		Long requesterId,
		PollType pollType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory
	) {
		boolean coffeeTemplate = pollType == PollType.COFFEE
			|| paymentCategory == PaymentCategory.COFFEE
			|| (chargeGenerationType == ChargeGenerationType.OPTION_PRICE && paymentCategory == PaymentCategory.COFFEE);
		if (coffeeTemplate) {
			pollAccessService.requireCoffeeTemplateManager(campusId, requesterId);
			return;
		}
		pollAccessService.requireTemplateManager(campusId, requesterId);
	}
}
