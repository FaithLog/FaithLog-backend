package com.faithlog.poll.infrastructure.adapter;

import com.faithlog.billing.service.ChargeCreationService;
import com.faithlog.billing.service.command.CreateCoffeeChargeCommand;
import com.faithlog.poll.service.port.CoffeePollChargeCommand;
import com.faithlog.poll.service.port.CoffeePollChargePort;
import org.springframework.stereotype.Component;

@Component
public class BillingCoffeePollChargeAdapter implements CoffeePollChargePort {

	private final ChargeCreationService chargeCreationService;

	public BillingCoffeePollChargeAdapter(ChargeCreationService chargeCreationService) {
		this.chargeCreationService = chargeCreationService;
	}

	@Override
	public void createOrUpdateCoffeeCharge(CoffeePollChargeCommand command) {
		chargeCreationService.createOrUpdateCoffeeCharge(new CreateCoffeeChargeCommand(
			command.campusId(),
			command.userId(),
			command.paymentAccountId(),
			command.pollResponseId(),
			command.title(),
			command.reason(),
			command.amount(),
			command.dueDate()
		));
	}
}
