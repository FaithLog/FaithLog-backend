package com.faithlog.poll.service.port;

import java.util.List;

public interface CoffeePollChargePort {

	void createOrUpdateCoffeeCharge(CoffeePollChargeCommand command);

	void createOrUpdateCoffeeCharges(List<CoffeePollChargeCommand> commands);
}
