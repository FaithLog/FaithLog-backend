package com.faithlog.devotion.infrastructure.billing;

import com.faithlog.billing.application.BillingService;
import com.faithlog.billing.application.CreatePenaltyChargeCommand;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.devotion.application.port.DevotionPenaltyChargeCommand;
import com.faithlog.devotion.application.port.DevotionPenaltyChargePort;
import org.springframework.stereotype.Component;

@Component
public class BillingDevotionPenaltyChargeAdapter implements DevotionPenaltyChargePort {

	private final BillingService billingService;

	public BillingDevotionPenaltyChargeAdapter(BillingService billingService) {
		this.billingService = billingService;
	}

	@Override
	public void requireActivePenaltyAccount(Long campusId) {
		billingService.requireActivePenaltyAccount(campusId);
	}

	@Override
	public void createPenaltyCharge(DevotionPenaltyChargeCommand command) {
		billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			command.campusId(),
			command.userId(),
			ChargeSourceType.DEVOTION_RECORD,
			command.weeklyRecordId(),
			command.title(),
			command.reason(),
			command.amount(),
			command.dueDate()
		));
	}
}
