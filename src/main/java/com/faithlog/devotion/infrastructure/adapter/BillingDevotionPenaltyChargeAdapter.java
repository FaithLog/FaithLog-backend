package com.faithlog.devotion.infrastructure.adapter;

import com.faithlog.billing.service.ChargeCreationService;
import com.faithlog.billing.service.PaymentAccountQueryService;
import com.faithlog.billing.service.command.CreatePenaltyChargeCommand;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.devotion.service.port.DevotionPenaltyChargeCommand;
import com.faithlog.devotion.service.port.DevotionPenaltyChargePort;
import org.springframework.stereotype.Component;

@Component
public class BillingDevotionPenaltyChargeAdapter implements DevotionPenaltyChargePort {

	private final PaymentAccountQueryService paymentAccountQueryService;
	private final ChargeCreationService chargeCreationService;

	public BillingDevotionPenaltyChargeAdapter(
		PaymentAccountQueryService paymentAccountQueryService,
		ChargeCreationService chargeCreationService
	) {
		this.paymentAccountQueryService = paymentAccountQueryService;
		this.chargeCreationService = chargeCreationService;
	}

	@Override
	public void requireActivePenaltyAccount(Long campusId) {
		paymentAccountQueryService.requireActivePenaltyAccount(campusId);
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
