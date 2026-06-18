package com.faithlog.billing.presentation;

import com.faithlog.billing.application.BillingService;
import com.faithlog.billing.application.BillingQueryService;
import com.faithlog.billing.application.AdminCampusChargeListQuery;
import com.faithlog.billing.application.AdminMemberChargeListQuery;
import com.faithlog.billing.application.ChargeItemResult;
import com.faithlog.billing.application.PaymentAccountResult;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.billing.presentation.dto.AdminCampusChargesResponse;
import com.faithlog.billing.presentation.dto.AdminMemberChargesResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
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
	private final BillingQueryService billingQueryService;

	public AdminBillingController(BillingService billingService, BillingQueryService billingQueryService) {
		this.billingService = billingService;
		this.billingQueryService = billingQueryService;
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

	@GetMapping("/campuses/{campusId}/charges")
	public ApiResponse<AdminCampusChargesResponse> listAdminCampusCharges(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@org.springframework.web.bind.annotation.RequestParam(required = false) PaymentCategory paymentCategory,
		@org.springframework.web.bind.annotation.RequestParam(required = false) ChargeStatus status,
		@org.springframework.web.bind.annotation.RequestParam(required = false) Long userId,
		@org.springframework.web.bind.annotation.RequestParam(required = false) String keyword,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		AdminCampusChargesResponse response = AdminCampusChargesResponse.from(
			billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
				campusId,
				authenticatedUser.userId(),
				paymentCategory,
				status,
				userId,
				keyword,
				BillingPageRequests.adminMembers(page, size, sort)
			))
		);
		return ApiResponse.success(response);
	}

	@GetMapping("/campuses/{campusId}/members/{userId}/charges")
	public ApiResponse<AdminMemberChargesResponse> listAdminMemberCharges(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long userId,
		@org.springframework.web.bind.annotation.RequestParam(required = false) PaymentCategory paymentCategory,
		@org.springframework.web.bind.annotation.RequestParam(required = false) ChargeStatus status,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		AdminMemberChargesResponse response = AdminMemberChargesResponse.from(
			billingQueryService.listAdminMemberCharges(new AdminMemberChargeListQuery(
				campusId,
				userId,
				authenticatedUser.userId(),
				paymentCategory,
				status,
				BillingPageRequests.chargeItems(page, size, sort)
			))
		);
		return ApiResponse.success(response);
	}
}
