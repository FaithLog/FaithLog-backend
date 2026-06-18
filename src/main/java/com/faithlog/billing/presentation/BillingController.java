package com.faithlog.billing.presentation;

import com.faithlog.billing.application.BillingService;
import com.faithlog.billing.application.ChargeItemResult;
import com.faithlog.billing.presentation.dto.ChargeItemResponse;
import com.faithlog.billing.presentation.dto.CompleteChargePaymentRequest;
import com.faithlog.billing.presentation.dto.PaymentAccountMemberResponse;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses")
public class BillingController {

	private final BillingService billingService;

	public BillingController(BillingService billingService) {
		this.billingService = billingService;
	}

	@GetMapping("/{campusId}/payment-accounts")
	public ApiResponse<List<PaymentAccountMemberResponse>> listPaymentAccounts(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		List<PaymentAccountMemberResponse> responses = billingService
			.listPaymentAccounts(campusId, authenticatedUser.userId())
			.stream()
			.map(PaymentAccountMemberResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}

	@PatchMapping(
		value = "/{campusId}/charges/me/{chargeItemId}/paid",
		consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE}
	)
	public ApiResponse<ChargeItemResponse> completeMyChargePayment(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long chargeItemId,
		@RequestBody(required = false) CompleteChargePaymentRequest request
	) {
		ChargeItemResult result = billingService.completeMyChargePayment(
			CompleteChargePaymentRequest.toCommand(request, campusId, chargeItemId, authenticatedUser)
		);
		return ApiResponse.success(ChargeItemResponse.from(result), "청구가 납부 완료 처리되었습니다.");
	}
}
