package com.faithlog.billing.service;

import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.command.CreateMealPaymentAccountCommand;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.campus.service.MealDutyAccessService;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealPaymentAccountService {

	private final MealDutyAccessService mealDutyAccessService;
	private final CampusRepositoryPort campusRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;

	public MealPaymentAccountService(
		MealDutyAccessService mealDutyAccessService,
		CampusRepositoryPort campusRepository,
		PaymentAccountRepositoryPort paymentAccountRepository
	) {
		this.mealDutyAccessService = mealDutyAccessService;
		this.campusRepository = campusRepository;
		this.paymentAccountRepository = paymentAccountRepository;
	}

	@Transactional
	public PaymentAccountResult create(CreateMealPaymentAccountCommand command) {
		mealDutyAccessService.requireActiveMealDuty(command.campusId(), command.requesterId());
		campusRepository.findByIdForUpdate(command.campusId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		paymentAccountRepository.findByCampusIdAndAccountTypeAndOwnerUserIdAndIsActiveTrueAndDeletedAtIsNull(
			command.campusId(), PaymentCategory.MEAL, command.requesterId())
			.ifPresent(account -> {
				account.deactivate();
				paymentAccountRepository.flush();
			});
		PaymentAccount account = paymentAccountRepository.save(PaymentAccount.create(
			command.campusId(),
			PaymentCategory.MEAL,
			command.nickname(),
			command.bankName(),
			command.accountNumber(),
			command.accountHolder(),
			command.requesterId()
		));
		return PaymentAccountResult.from(account);
	}

	@Transactional(readOnly = true)
	public List<PaymentAccountResult> listMine(Long campusId, Long requesterId, boolean includeInactive) {
		mealDutyAccessService.requireActiveMealDuty(campusId, requesterId);
		List<PaymentAccount> accounts = includeInactive
			? paymentAccountRepository.findByCampusIdAndOwnerUserIdAndAccountTypeAndDeletedAtIsNullOrderByIdAsc(
				campusId, requesterId, PaymentCategory.MEAL)
			: paymentAccountRepository.findByCampusIdAndOwnerUserIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(
				campusId, requesterId, PaymentCategory.MEAL);
		return accounts.stream().map(PaymentAccountResult::from).toList();
	}

	@Transactional
	public PaymentAccountResult deactivate(Long campusId, Long accountId, Long requesterId) {
		mealDutyAccessService.requireActiveMealDuty(campusId, requesterId);
		PaymentAccount account = paymentAccountRepository.findById(accountId)
			.filter(candidate -> !candidate.isDeleted())
			.filter(candidate -> candidate.campusId().equals(campusId))
			.filter(candidate -> candidate.accountType() == PaymentCategory.MEAL)
			.filter(candidate -> candidate.ownerUserId().equals(requesterId))
			.orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PAYMENT_ACCOUNT_NOT_FOUND));
		account.deactivate();
		return PaymentAccountResult.from(account);
	}
}
