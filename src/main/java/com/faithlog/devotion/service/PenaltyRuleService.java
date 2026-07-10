package com.faithlog.devotion.service;

import com.faithlog.devotion.service.command.CreatePenaltyRuleCommand;
import com.faithlog.devotion.service.command.UpdatePenaltyRuleCommand;
import com.faithlog.devotion.service.result.PenaltyRuleResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PenaltyRuleService {

	private final PenaltyRuleCommandService penaltyRuleCommandService;
	private final PenaltyRuleQueryService penaltyRuleQueryService;

	public PenaltyRuleService(
		PenaltyRuleCommandService penaltyRuleCommandService,
		PenaltyRuleQueryService penaltyRuleQueryService
	) {
		this.penaltyRuleCommandService = penaltyRuleCommandService;
		this.penaltyRuleQueryService = penaltyRuleQueryService;
	}

	public List<PenaltyRuleResult> listPenaltyRules(Long campusId, Long requesterId) {
		return penaltyRuleQueryService.listPenaltyRules(campusId, requesterId);
	}

	public PenaltyRuleResult createPenaltyRule(CreatePenaltyRuleCommand command) {
		return penaltyRuleCommandService.createPenaltyRule(command);
	}

	public PenaltyRuleResult updatePenaltyRule(UpdatePenaltyRuleCommand command) {
		return penaltyRuleCommandService.updatePenaltyRule(command);
	}
}
