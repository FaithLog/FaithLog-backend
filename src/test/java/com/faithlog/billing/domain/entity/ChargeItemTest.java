package com.faithlog.billing.domain.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
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
		assertThat(charge.amount()).isEqualTo(1000);
	}

	@Test
	void reactivateCanceledCharge_reuses_row_with_latest_amount_and_account_snapshot() {
		PaymentAccount replacement = PaymentAccount.create(
			1L, PaymentCategory.PENALTY, "새 벌금 계좌", "새은행", "456", "새회계", 1L
		);
		org.springframework.test.util.ReflectionTestUtils.setField(replacement, "id", 2L);
		ChargeItem charge = createCharge(1000);
		charge.cancel();

		charge.reactivateCanceledCharge(replacement, "새 제목", "새 사유", 2500, LocalDate.of(2026, 7, 20));

		assertThat(charge.status()).isEqualTo(ChargeStatus.UNPAID);
		assertThat(charge.paymentAccountId()).isEqualTo(2L);
		assertThat(charge.bankNameSnapshot()).isEqualTo("새은행");
		assertThat(charge.accountNumberSnapshot()).isEqualTo("456");
		assertThat(charge.accountHolderSnapshot()).isEqualTo("새회계");
		assertThat(charge.title()).isEqualTo("새 제목");
		assertThat(charge.reason()).isEqualTo("새 사유");
		assertThat(charge.amount()).isEqualTo(2500);
		assertThat(charge.dueDate()).isEqualTo(LocalDate.of(2026, 7, 20));
		assertThat(charge.paidAt()).isNull();
	}

	private ChargeItem createCharge(int amount) {
		return ChargeItem.create(
			1L, 1L, PaymentCategory.PENALTY, 1L, "은행", "123", "회계",
			ChargeSourceType.DEVOTION_RECORD, 1L, "제목", "사유", amount, LocalDate.now()
		);
	}
}
