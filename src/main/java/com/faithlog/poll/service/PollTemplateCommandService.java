package com.faithlog.poll.service;

import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.PollTemplate;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollTemplateRepository;
import com.faithlog.poll.service.command.CreatePollTemplateCommand;
import com.faithlog.poll.service.command.UpdatePollTemplateCommand;
import com.faithlog.poll.service.result.PollTemplateResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollTemplateCommandService {

	private final PollTemplateRepository pollTemplateRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final PollTemplateOptionSupport optionSupport;
	private final PollAccessService pollAccessService;

	public PollTemplateCommandService(
		PollTemplateRepository pollTemplateRepository,
		PaymentAccountRepositoryPort paymentAccountRepository,
		PollTemplateOptionSupport optionSupport,
		PollAccessService pollAccessService
	) {
		this.pollTemplateRepository = pollTemplateRepository;
		this.paymentAccountRepository = paymentAccountRepository;
		this.optionSupport = optionSupport;
		this.pollAccessService = pollAccessService;
	}

	@Transactional
	public PollTemplateResult createTemplate(CreatePollTemplateCommand command) {
		if (command.pollType() == PollType.MEAL || command.paymentCategory() == PaymentCategory.MEAL) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED, "MEAL 투표는 템플릿을 지원하지 않습니다.");
		}
		requireTemplateManageAccess(
			command.campusId(),
			command.requesterId(),
			command.pollType(),
			command.chargeGenerationType(),
			command.paymentCategory()
		);
		requirePaymentAccountIfNeeded(
			command.pollType(),
			command.chargeGenerationType(),
			command.paymentCategory(),
			command.paymentAccountId(),
			command.campusId(),
			command.requesterId()
		);
		List<PollOptionSnapshot> snapshots = optionSupport.resolve(command.pollType(), command.options());
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
		optionSupport.save(template.id(), snapshots);
		return toResult(template);
	}

	@Transactional
	public PollTemplateResult updateTemplate(UpdatePollTemplateCommand command) {
		PollTemplate template = pollTemplateRepository.findById(command.templateId())
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		requireSameCampusScope(template, command.campusId());
		if (template.pollType() == PollType.MEAL || command.paymentCategory() == PaymentCategory.MEAL) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED, "MEAL 투표는 템플릿을 지원하지 않습니다.");
		}
		requirePersistedTemplateManageAccess(
			command.campusId(),
			command.requesterId(),
			template.pollType()
		);
		requirePaymentAccountIfNeeded(
			template.pollType(),
			command.chargeGenerationType(),
			command.paymentCategory(),
			command.paymentAccountId(),
			command.campusId(),
			command.requesterId()
		);
		List<PollOptionSnapshot> snapshots = optionSupport.resolve(template.pollType(), command.options());
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
		optionSupport.replace(template.id(), snapshots);
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

	private void requireSameCampusScope(PollTemplate template, Long campusId) {
		if (!template.campusId().equals(campusId)) {
			throw new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND);
		}
	}

	private PollTemplateResult toResult(PollTemplate template) {
		return PollTemplateResult.of(template, optionSupport.results(template.id()));
	}

	private void requirePaymentAccountIfNeeded(
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

	private void requirePersistedTemplateManageAccess(
		Long campusId,
		Long requesterId,
		PollType persistedPollType
	) {
		if (persistedPollType == PollType.COFFEE) {
			pollAccessService.requireCoffeeTemplateManager(campusId, requesterId);
			return;
		}
		pollAccessService.requireTemplateManager(campusId, requesterId);
	}
}
