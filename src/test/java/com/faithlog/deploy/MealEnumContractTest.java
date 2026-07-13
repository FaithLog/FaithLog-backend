package com.faithlog.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.poll.domain.type.PollType;
import org.junit.jupiter.api.Test;

class MealEnumContractTest {

	@Test
	void mealIsAnApprovedDutyPollAndPaymentCategory() {
		assertThat(Enum.valueOf(DutyType.class, "MEAL").name()).isEqualTo("MEAL");
		assertThat(Enum.valueOf(PollType.class, "MEAL").name()).isEqualTo("MEAL");
		assertThat(Enum.valueOf(PaymentCategory.class, "MEAL").name()).isEqualTo("MEAL");
	}
}
