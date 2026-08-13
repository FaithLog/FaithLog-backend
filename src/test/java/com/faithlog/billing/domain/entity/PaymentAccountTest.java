package com.faithlog.billing.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.billing.domain.type.PaymentCategory;
import org.junit.jupiter.api.Test;

class PaymentAccountTest {

	@Test
	void updateDetails_changes_only_editable_account_fields() {
		PaymentAccount account = PaymentAccount.create(
			257L,
			PaymentCategory.COFFEE,
			"기존 계좌",
			"기존은행",
			"257-OLD",
			"기존예금주",
			1257L
		);

		account.updateDetails("새 계좌", "새은행", "257-NEW", "새예금주");

		assertThat(account.nickname()).isEqualTo("새 계좌");
		assertThat(account.bankName()).isEqualTo("새은행");
		assertThat(account.accountNumber()).isEqualTo("257-NEW");
		assertThat(account.accountHolder()).isEqualTo("새예금주");
		assertThat(account.campusId()).isEqualTo(257L);
		assertThat(account.accountType()).isEqualTo(PaymentCategory.COFFEE);
		assertThat(account.ownerUserId()).isEqualTo(1257L);
		assertThat(account.isActive()).isTrue();
		assertThat(account.isDeleted()).isFalse();
	}
}
