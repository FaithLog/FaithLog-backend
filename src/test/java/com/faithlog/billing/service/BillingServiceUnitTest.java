package com.faithlog.billing.service;

import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.service.result.PaymentAccountResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.domain.entity.Campus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BillingServiceUnitTest {

	@Mock
	private PaymentAccountRepositoryPort paymentAccountRepository;

	@Mock
	private ChargeItemRepositoryPort chargeItemRepository;

	@Mock
	private CampusRepositoryPort campusRepository;

	@Mock
	private CampusMemberRepositoryPort campusMemberRepository;

	@Mock
	private CampusUserLookupPort userLookupPort;

	@Mock
	private CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;

	@InjectMocks
	private BillingService billingService;

	@Test
	void createPenaltyPaymentAccount_flushes_deactivation_before_saving_replacement() {
		Long campusId = 125L;
		Long requesterId = 1L;
		PaymentAccount activePenalty = PaymentAccount.create(
			campusId,
			PaymentCategory.PENALTY,
			"기존 벌금 계좌",
			"하나은행",
			"125-OLD",
			"이전회계",
			requesterId
		);
		when(userLookupPort.findCampusUserById(requesterId))
			.thenReturn(Optional.of(new CampusUserLookupResult(requesterId, "관리자", "manager@example.com", "ADMIN", true)));
		when(campusRepository.findByIdForUpdate(campusId))
			.thenReturn(Optional.of(Campus.create("125캠", "분당", "테스트", "INVITE125")));
		when(paymentAccountRepository.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(
			campusId,
			PaymentCategory.PENALTY
		)).thenReturn(Optional.of(activePenalty));
		when(paymentAccountRepository.save(any(PaymentAccount.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(chargeItemRepository.findByCampusIdAndPaymentCategoryAndStatus(
			campusId,
			PaymentCategory.PENALTY,
			ChargeStatus.UNPAID
		)).thenReturn(List.of());

		PaymentAccountResult result = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId,
			requesterId,
			PaymentCategory.PENALTY,
			"새 벌금 계좌",
			"국민은행",
			"125-NEW",
			"현재회계",
			null
		));

		InOrder orderedRepositoryCalls = inOrder(paymentAccountRepository);
		orderedRepositoryCalls.verify(paymentAccountRepository)
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(campusId, PaymentCategory.PENALTY);
		orderedRepositoryCalls.verify(paymentAccountRepository).flush();
		orderedRepositoryCalls.verify(paymentAccountRepository).save(any(PaymentAccount.class));
		assertThat(activePenalty.isActive()).isFalse();
		assertThat(result.accountNumber()).isEqualTo("125-NEW");
	}

	@Test
	void activatePenaltyPaymentAccount_flushes_existing_active_deactivation_before_target_activation() {
		Long campusId = 125L;
		Long requesterId = 1L;
		PaymentAccount targetPenalty = PaymentAccount.create(
			campusId,
			PaymentCategory.PENALTY,
			"비활성 벌금 계좌",
			"하나은행",
			"125-INACTIVE",
			"이전회계",
			requesterId
		);
		PaymentAccount activePenalty = PaymentAccount.create(
			campusId,
			PaymentCategory.PENALTY,
			"현재 벌금 계좌",
			"국민은행",
			"125-ACTIVE",
			"현재회계",
			requesterId
		);
		ReflectionTestUtils.setField(targetPenalty, "id", 10L);
		ReflectionTestUtils.setField(activePenalty, "id", 11L);
		targetPenalty.deactivate();
		when(campusRepository.findByIdForUpdate(campusId))
			.thenReturn(Optional.of(Campus.create("125캠", "분당", "테스트", "INVITE125")));
		when(paymentAccountRepository.findById(targetPenalty.id()))
			.thenReturn(Optional.of(targetPenalty));
		when(userLookupPort.findCampusUserById(requesterId))
			.thenReturn(Optional.of(new CampusUserLookupResult(requesterId, "관리자", "manager@example.com", "ADMIN", true)));
		when(paymentAccountRepository.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(
			campusId,
			PaymentCategory.PENALTY
		)).thenReturn(Optional.of(activePenalty));
		when(chargeItemRepository.findByCampusIdAndPaymentCategoryAndStatus(
			campusId,
			PaymentCategory.PENALTY,
			ChargeStatus.UNPAID
		)).thenReturn(List.of());

		PaymentAccountResult result = billingService.activatePenaltyPaymentAccount(campusId, targetPenalty.id(), requesterId);

		InOrder orderedRepositoryCalls = inOrder(paymentAccountRepository);
		orderedRepositoryCalls.verify(paymentAccountRepository)
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(campusId, PaymentCategory.PENALTY);
		orderedRepositoryCalls.verify(paymentAccountRepository).flush();
		assertThat(activePenalty.isActive()).isFalse();
		assertThat(targetPenalty.isActive()).isTrue();
		assertThat(result.id()).isEqualTo(targetPenalty.id());
	}
}
