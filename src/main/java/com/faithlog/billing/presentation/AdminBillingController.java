package com.faithlog.billing.presentation;

import com.faithlog.billing.application.BillingService;
import com.faithlog.billing.application.ChargeItemResult;
import com.faithlog.billing.application.PaymentAccountResult;
import com.faithlog.billing.presentation.dto.ChangeChargeStatusRequest;
import com.faithlog.billing.presentation.dto.ChargeItemResponse;
import com.faithlog.billing.presentation.dto.CreatePaymentAccountRequest;
import com.faithlog.billing.presentation.dto.PaymentAccountAdminResponse;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminBillingController {

	private final BillingService billingService;

	public AdminBillingController(BillingService billingService) {
		this.billingService = billingService;
	}

	@PostMapping("/campuses/{campusId}/payment-accounts")
	public ResponseEntity<ApiResponse<PaymentAccountAdminResponse>> createPaymentAccount(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreatePaymentAccountRequest request
	) {
		PaymentAccountResult result = billingService.createPaymentAccount(request.toCommand(campusId, authenticatedUser));
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(PaymentAccountAdminResponse.from(result), "납부 계좌가 등록되었습니다."));
	}

	@PatchMapping("/payment-accounts/{accountId}/deactivate")
	public ApiResponse<PaymentAccountAdminResponse> deactivatePaymentAccount(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long accountId
	) {
		PaymentAccountResult result = billingService.deactivatePaymentAccount(accountId, authenticatedUser.userId());
		return ApiResponse.success(PaymentAccountAdminResponse.from(result), "납부 계좌가 비활성화되었습니다.");
	}

	@PatchMapping("/charges/{chargeItemId}/status")
	public ApiResponse<ChargeItemResponse> changeChargeStatus(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long chargeItemId,
		@Valid @RequestBody ChangeChargeStatusRequest request
	) {
		ChargeItemResult result = billingService.changeChargeStatus(request.toCommand(chargeItemId, authenticatedUser));
		return ApiResponse.success(ChargeItemResponse.from(result), "청구 상태가 변경되었습니다.");
	}
}
