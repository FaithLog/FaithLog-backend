package com.faithlog.devotion.infrastructure.adapter;

import com.faithlog.billing.service.BillingQueryService;
import com.faithlog.billing.service.ChargeCreationService;
import com.faithlog.billing.service.command.CreatePenaltyChargeCommand;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.devotion.service.port.DevotionPenaltyChargeCommand;
import com.faithlog.devotion.service.port.DevotionPenaltyChargePort;
import org.springframework.stereotype.Component;

@Component
public class BillingDevotionPenaltyChargeAdapter implements DevotionPenaltyChargePort {

	private final BillingQueryService billingQueryService;
	private final ChargeCreationService chargeCreationService;

	public BillingDevotionPenaltyChargeAdapter(
		BillingQueryService billingQueryService,
		ChargeCreationService chargeCreationService
	) {
		this.billingQueryService = billingQueryService;
		this.chargeCreationService = chargeCreationService;
	}

	@Override
	public void requireActivePenaltyAccount(Long campusId) {
		billingQueryService.requireActivePenaltyAccount(campusId);
	}

	@Override
	public void createPenaltyCharge(DevotionPenaltyChargeCommand command) {
		chargeCreationService.createPenaltyCharge(new CreatePenaltyChargeCommand(
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
