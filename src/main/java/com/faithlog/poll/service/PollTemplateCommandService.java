package com.faithlog.poll.service;

import com.faithlog.billing.domain.type.PaymentCategory;
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
	private final PollTemplateOptionSupport optionSupport;
	private final PollAccessService pollAccessService;

	public PollTemplateCommandService(
		PollTemplateRepository pollTemplateRepository,
		PollTemplateOptionSupport optionSupport,
		PollAccessService pollAccessService
	) {
		this.pollTemplateRepository = pollTemplateRepository;
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
		CoffeeOperationClassifier.requireConsistentConfiguration(
			command.pollType(), command.chargeGenerationType(), command.paymentCategory());
		List<PollOptionSnapshot> snapshots = optionSupport.resolve(command.pollType(), command.options());
		PollTemplate template = pollTemplateRepository.save(PollTemplate.create(
			command.campusId(),
			command.title(),
			command.pollType(),
			command.selectionType(),
			command.chargeGenerationType(),
			command.paymentCategory(),
			CoffeeOperationClassifier.isCoffeeOperation(
				command.pollType(), command.chargeGenerationType(), command.paymentCategory()) ? null : command.paymentAccountId(),
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
		PollTemplateRepository.PollTemplateLockScope scope = pollTemplateRepository.findLockScopeById(command.templateId())
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		requireSameCampusScope(scope.getCampusId(), command.campusId());
		requirePersistedTemplateManageAccess(
			command.campusId(),
			command.requesterId(),
			scope.getPollType(),
			scope.getChargeGenerationType(),
			scope.getPaymentCategory()
		);
		PollTemplate template = pollTemplateRepository.findByIdForUpdate(command.templateId())
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		requireSameCampusScope(template, command.campusId());
		CoffeeOperationClassifier.requireConsistentConfiguration(
			template.pollType(), template.chargeGenerationType(), template.paymentCategory());
		if (!template.isActive()) {
			throw new BusinessException(ErrorCode.POLL_TEMPLATE_INACTIVE);
		}
		requireTemplateManageAccess(
			command.campusId(), command.requesterId(), template.pollType(),
			command.chargeGenerationType(), command.paymentCategory());
		if (template.pollType() == PollType.MEAL || command.paymentCategory() == PaymentCategory.MEAL) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED, "MEAL 투표는 템플릿을 지원하지 않습니다.");
		}
		CoffeeOperationClassifier.requireConsistentConfiguration(
			template.pollType(), command.chargeGenerationType(), command.paymentCategory());
		List<PollOptionSnapshot> snapshots = optionSupport.resolve(template.pollType(), command.options());
		template.update(
			command.title(),
			command.selectionType(),
			command.chargeGenerationType(),
			command.paymentCategory(),
			CoffeeOperationClassifier.isCoffeeOperation(
				template.pollType(), command.chargeGenerationType(), command.paymentCategory()) ? null : command.paymentAccountId(),
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
		PollTemplateRepository.PollTemplateLockScope scope = pollTemplateRepository.findLockScopeById(templateId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		requireSameCampusScope(scope.getCampusId(), campusId);
		if (CoffeeOperationClassifier.isCoffeeOperation(
			scope.getPollType(), scope.getChargeGenerationType(), scope.getPaymentCategory())) {
			pollAccessService.requireCoffeeTemplateManagerForUpdate(campusId, requesterId);
		} else {
			pollAccessService.requireTemplateManager(campusId, requesterId);
		}
		PollTemplate template = pollTemplateRepository.findByIdForUpdate(templateId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		requireSameCampusScope(template, campusId);
		CoffeeOperationClassifier.requireConsistentConfiguration(
			template.pollType(), template.chargeGenerationType(), template.paymentCategory());
		template.deactivate();
		return toResult(template);
	}

	private void requireSameCampusScope(PollTemplate template, Long campusId) {
		requireSameCampusScope(template.campusId(), campusId);
	}

	private void requireSameCampusScope(Long persistedCampusId, Long campusId) {
		if (!persistedCampusId.equals(campusId)) {
			throw new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND);
		}
	}

	private PollTemplateResult toResult(PollTemplate template) {
		return PollTemplateResult.of(template, optionSupport.results(template.id()));
	}

	private void requireTemplateManageAccess(
		Long campusId,
		Long requesterId,
		PollType pollType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory
	) {
		boolean coffeeTemplate = CoffeeOperationClassifier.isCoffeeOperation(
			pollType, chargeGenerationType, paymentCategory);
		if (coffeeTemplate) {
			pollAccessService.requireCoffeeTemplateManagerForUpdate(campusId, requesterId);
			return;
		}
		pollAccessService.requireTemplateManager(campusId, requesterId);
	}

	private void requirePersistedTemplateManageAccess(
		Long campusId,
		Long requesterId,
		PollType pollType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory
	) {
		if (CoffeeOperationClassifier.isCoffeeOperation(
			pollType, chargeGenerationType, paymentCategory)) {
			pollAccessService.requireCoffeeTemplateManagerForUpdate(campusId, requesterId);
			return;
		}
		pollAccessService.requireTemplateManager(campusId, requesterId);
	}
}
