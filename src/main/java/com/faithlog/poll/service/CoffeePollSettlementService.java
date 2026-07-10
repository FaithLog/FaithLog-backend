package com.faithlog.poll.service;

import org.springframework.stereotype.Service;

@Service
public class CoffeePollSettlementService {

	private final CoffeePollSettlementCommandService commandService;

	public CoffeePollSettlementService(CoffeePollSettlementCommandService commandService) {
		this.commandService = commandService;
	}

	public void settleClosedCoffeePoll(Long campusId, Long pollId) {
		commandService.settleClosedCoffeePoll(campusId, pollId);
	}
}
