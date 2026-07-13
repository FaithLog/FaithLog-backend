package com.faithlog.billing.domain.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ChargeItemTest {

	@Test
	void create_rejects_zero_and_negative_amounts() {
		assertThatThrownBy(() -> createCharge(0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> createCharge(-1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void update_rejects_zero_and_negative_amounts() {
		PaymentAccount account = PaymentAccount.create(
			1L, PaymentCategory.PENALTY, "벌금 계좌", "은행", "123", "회계", 1L
		);
		ChargeItem charge = createCharge(1000);

		assertThatThrownBy(() -> charge.updateUnpaidCharge(account, "제목", "사유", 0, LocalDate.now()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> charge.updateUnpaidCharge(account, "제목", "사유", -1, LocalDate.now()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private ChargeItem createCharge(int amount) {
		return ChargeItem.create(
			1L, 1L, PaymentCategory.PENALTY, 1L, "은행", "123", "회계",
			ChargeSourceType.DEVOTION_RECORD, 1L, "제목", "사유", amount, LocalDate.now()
		);
	}
}
