package com.faithlog.poll.infrastructure.billing;

import com.faithlog.billing.service.BillingService;
import com.faithlog.billing.service.command.CreateCoffeeChargeCommand;
import com.faithlog.poll.application.port.CoffeePollChargeCommand;
import com.faithlog.poll.application.port.CoffeePollChargePort;
import org.springframework.stereotype.Component;

@Component
public class BillingCoffeePollChargeAdapter implements CoffeePollChargePort {

	private final BillingService billingService;

	public BillingCoffeePollChargeAdapter(BillingService billingService) {
		this.billingService = billingService;
	}

	@Override
	public void createOrUpdateCoffeeCharge(CoffeePollChargeCommand command) {
		billingService.createOrUpdateCoffeeCharge(new CreateCoffeeChargeCommand(
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
