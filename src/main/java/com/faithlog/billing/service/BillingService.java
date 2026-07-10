package com.faithlog.billing.service;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.command.ChangeChargeStatusCommand;
import com.faithlog.billing.service.command.CompleteChargePaymentCommand;
import com.faithlog.billing.service.command.CreateCoffeeChargeCommand;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.service.command.CreatePenaltyChargeCommand;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.billing.service.result.PaymentAccountResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BillingService {

	private final PaymentAccountCommandService paymentAccountCommandService;
	private final ChargeCreationService chargeCreationService;
	private final ChargeStatusCommandService chargeStatusCommandService;
	private final PaymentAccountQueryService paymentAccountQueryService;

	public BillingService(
		PaymentAccountCommandService paymentAccountCommandService,
		ChargeCreationService chargeCreationService,
		ChargeStatusCommandService chargeStatusCommandService,
		PaymentAccountQueryService paymentAccountQueryService
	) {
		this.paymentAccountCommandService = paymentAccountCommandService;
		this.chargeCreationService = chargeCreationService;
		this.chargeStatusCommandService = chargeStatusCommandService;
		this.paymentAccountQueryService = paymentAccountQueryService;
	}

	public PaymentAccountResult createPaymentAccount(CreatePaymentAccountCommand command) {
		return paymentAccountCommandService.createPaymentAccount(command);
	}

	public PaymentAccountResult deactivatePaymentAccount(Long accountId, Long requesterId) {
		return paymentAccountCommandService.deactivatePaymentAccount(accountId, requesterId);
	}

	public PaymentAccountResult activatePenaltyPaymentAccount(Long campusId, Long paymentAccountId, Long requesterId) {
		return paymentAccountCommandService.activatePenaltyPaymentAccount(campusId, paymentAccountId, requesterId);
	}

	public void deletePaymentAccount(Long campusId, Long paymentAccountId, Long requesterId) {
		paymentAccountCommandService.deletePaymentAccount(campusId, paymentAccountId, requesterId);
	}

	public List<PaymentAccountResult> listPaymentAccounts(Long campusId, Long requesterId) {
		return paymentAccountQueryService.listPaymentAccounts(campusId, requesterId);
	}

	public List<PaymentAccountResult> listAdminPaymentAccounts(Long campusId, Long requesterId) {
		return paymentAccountQueryService.listAdminPaymentAccounts(campusId, requesterId);
	}

	public List<PaymentAccountResult> listAdminPaymentAccounts(
		Long campusId,
		Long requesterId,
		PaymentCategory accountType,
		boolean includeInactive
	) {
		return paymentAccountQueryService.listAdminPaymentAccounts(campusId, requesterId, accountType, includeInactive);
	}

	public void requireActivePenaltyAccount(Long campusId) {
		paymentAccountQueryService.requireActivePenaltyAccount(campusId);
	}

	public ChargeItemResult createPenaltyCharge(CreatePenaltyChargeCommand command) {
		return chargeCreationService.createPenaltyCharge(command);
	}

	public ChargeItemResult createOrUpdateCoffeeCharge(CreateCoffeeChargeCommand command) {
		return chargeCreationService.createOrUpdateCoffeeCharge(command);
	}

	public ChargeItemResult completeMyChargePayment(CompleteChargePaymentCommand command) {
		return chargeStatusCommandService.completeMyChargePayment(command);
	}

	public ChargeItemResult changeChargeStatus(ChangeChargeStatusCommand command) {
		return chargeStatusCommandService.changeChargeStatus(command);
	}
}
