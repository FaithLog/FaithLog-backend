package com.faithlog.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.command.CreateCoffeeChargeCommand;
import com.faithlog.billing.service.command.CreateMealChargeCommand;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeCreationServiceBatchUnitTest {

	@Mock
	private PaymentAccountRepositoryPort paymentAccountRepository;

	@Mock
	private ChargeItemRepositoryPort chargeItemRepository;

	@InjectMocks
	private ChargeCreationService chargeCreationService;

	@Test
	void coffee_batch_loads_account_and_existing_charges_once_while_preserving_unpaid_and_terminal_semantics() {
		PaymentAccount account = account(PaymentCategory.COFFEE, 10L);
		ChargeItem unpaid = charge(1L, PaymentCategory.COFFEE, account, 101L, "기존", 1000);
		ChargeItem paid = charge(2L, PaymentCategory.COFFEE, account, 102L, "완료", 2000);
		paid.markPaid();
		List<CreateCoffeeChargeCommand> commands = List.of(
			new CreateCoffeeChargeCommand(1L, 1L, 10L, 101L, "수정", "주문", 3000, null),
			new CreateCoffeeChargeCommand(1L, 2L, 10L, 102L, "무시", "주문", 4000, null),
			new CreateCoffeeChargeCommand(1L, 3L, 10L, 103L, "신규", "주문", 5000, null)
		);
		when(paymentAccountRepository.findById(10L)).thenReturn(Optional.of(account));
		when(chargeItemRepository.findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInForUpdate(
			1L, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE, List.of(101L, 102L, 103L)
		)).thenReturn(List.of(unpaid, paid));

		chargeCreationService.createOrUpdateCoffeeCharges(commands);

		verify(paymentAccountRepository, times(1)).findById(10L);
		verify(chargeItemRepository, times(1)).findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInForUpdate(
			1L, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE, List.of(101L, 102L, 103L)
		);
		verify(chargeItemRepository, never()).findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceIdForUpdate(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
		);
		ArgumentCaptor<List<ChargeItem>> saved = listCaptor();
		verify(chargeItemRepository).saveAllCharges(saved.capture());
		assertThat(saved.getValue()).singleElement().satisfies(created -> {
			assertThat(created.userId()).isEqualTo(3L);
			assertThat(created.sourceId()).isEqualTo(103L);
			assertThat(created.amount()).isEqualTo(5000);
		});
		assertThat(unpaid.title()).isEqualTo("수정");
		assertThat(unpaid.amount()).isEqualTo(3000);
		assertThat(paid.title()).isEqualTo("완료");
		assertThat(paid.amount()).isEqualTo(2000);
	}

	@Test
	void meal_batch_validates_account_and_duplicate_sources_once_then_saves_all_charges() {
		PaymentAccount account = account(PaymentCategory.MEAL, 7L);
		List<CreateMealChargeCommand> commands = List.of(
			new CreateMealChargeCommand(1L, 1L, 7L, 20L, 201L, "A", 5000),
			new CreateMealChargeCommand(1L, 2L, 7L, 20L, 202L, "B", 6000)
		);
		when(paymentAccountRepository.findById(20L)).thenReturn(Optional.of(account));
		when(chargeItemRepository.findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInForUpdate(
			1L, PaymentCategory.MEAL, ChargeSourceType.POLL_RESPONSE, List.of(201L, 202L)
		)).thenReturn(List.of());

		chargeCreationService.createMealCharges(commands);

		verify(paymentAccountRepository, times(1)).findById(20L);
		verify(chargeItemRepository, times(1)).findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInForUpdate(
			1L, PaymentCategory.MEAL, ChargeSourceType.POLL_RESPONSE, List.of(201L, 202L)
		);
		verify(chargeItemRepository, never()).findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
		);
		ArgumentCaptor<List<ChargeItem>> saved = listCaptor();
		verify(chargeItemRepository).saveAllCharges(saved.capture());
		assertThat(saved.getValue()).extracting(ChargeItem::sourceId).containsExactly(201L, 202L);
		assertThat(saved.getValue()).extracting(ChargeItem::amount).containsExactly(5000, 6000);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private ArgumentCaptor<List<ChargeItem>> listCaptor() {
		return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
	}

	private PaymentAccount account(PaymentCategory category, Long ownerId) {
		PaymentAccount account = org.mockito.Mockito.mock(PaymentAccount.class);
		when(account.id()).thenReturn(category == PaymentCategory.COFFEE ? 10L : 20L);
		when(account.campusId()).thenReturn(1L);
		if (category == PaymentCategory.MEAL) {
			when(account.ownerUserId()).thenReturn(ownerId);
		}
		when(account.accountType()).thenReturn(category);
		when(account.isActive()).thenReturn(true);
		when(account.isDeleted()).thenReturn(false);
		when(account.bankName()).thenReturn("은행");
		when(account.accountNumber()).thenReturn("123");
		when(account.accountHolder()).thenReturn("소유자");
		return account;
	}

	private ChargeItem charge(
		Long userId,
		PaymentCategory category,
		PaymentAccount account,
		Long sourceId,
		String title,
		int amount
	) {
		return ChargeItem.create(
			1L, userId, category, account.id(), account.bankName(), account.accountNumber(), account.accountHolder(),
			ChargeSourceType.POLL_RESPONSE, sourceId, title, null, amount, null
		);
	}
}
