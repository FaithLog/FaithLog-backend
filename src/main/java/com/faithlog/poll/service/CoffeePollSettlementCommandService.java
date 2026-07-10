package com.faithlog.poll.service;

import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.service.CoffeePollSettlementSupport.SettlementContext;
import com.faithlog.poll.service.port.CoffeePollChargePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoffeePollSettlementCommandService {

	private final CoffeePollSettlementSupport settlementSupport;
	private final CoffeePollChargePort coffeePollChargePort;

	public CoffeePollSettlementCommandService(
		CoffeePollSettlementSupport settlementSupport,
		CoffeePollChargePort coffeePollChargePort
	) {
		this.settlementSupport = settlementSupport;
		this.coffeePollChargePort = coffeePollChargePort;
	}

	@Transactional
	public void settleClosedCoffeePoll(Long campusId, Long pollId) {
		SettlementContext context = settlementSupport.prepare(campusId, pollId);
		if (context == null) {
			return;
		}
		for (PollResponse response : context.responses()) {
			coffeePollChargePort.createOrUpdateCoffeeCharge(settlementSupport.chargeCommand(context, response));
		}
	}
}
