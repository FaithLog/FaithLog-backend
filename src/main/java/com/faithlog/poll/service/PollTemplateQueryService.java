package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.PollTemplate;
import com.faithlog.poll.infrastructure.repository.PollTemplateRepository;
import com.faithlog.poll.service.result.PollTemplateResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollTemplateQueryService {

	private final PollTemplateRepository pollTemplateRepository;
	private final PollTemplateOptionSupport optionSupport;
	private final PollAccessService pollAccessService;

	public PollTemplateQueryService(
		PollTemplateRepository pollTemplateRepository,
		PollTemplateOptionSupport optionSupport,
		PollAccessService pollAccessService
	) {
		this.pollTemplateRepository = pollTemplateRepository;
		this.optionSupport = optionSupport;
		this.pollAccessService = pollAccessService;
	}

	@Transactional(readOnly = true)
	public List<PollTemplateResult> listTemplates(Long campusId, Long requesterId) {
		boolean manager = pollAccessService.hasAdminVisibility(campusId, requesterId);
		if (!manager) {
			pollAccessService.requireCoffeeTemplateManager(campusId, requesterId);
		}
		return pollTemplateRepository.findByCampusIdAndIsActiveTrueOrderByIdAsc(campusId)
			.stream()
			.filter(template -> manager || CoffeeOperationClassifier.isCoffeeOperation(
				template.pollType(), template.chargeGenerationType(), template.paymentCategory()))
			.map(this::toResult)
			.toList();
	}

	@Transactional(readOnly = true)
	public PollTemplateResult getTemplate(Long campusId, Long templateId, Long requesterId) {
		PollTemplate template = pollTemplateRepository.findById(templateId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		if (!template.campusId().equals(campusId)) {
			throw new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND);
		}
		if (pollAccessService.hasAdminVisibility(campusId, requesterId)) {
			return toResult(template);
		}
		if (!CoffeeOperationClassifier.isCoffeeOperation(
			template.pollType(), template.chargeGenerationType(), template.paymentCategory())) {
			throw new BusinessException(ErrorCode.POLL_TEMPLATE_MANAGE_FORBIDDEN);
		}
		pollAccessService.requireCoffeeTemplateManager(campusId, requesterId);
		return toResult(template);
	}

	private PollTemplateResult toResult(PollTemplate template) {
		return PollTemplateResult.of(template, optionSupport.results(template.id()));
	}
}
