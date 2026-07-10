package com.faithlog.billing.controller;

import com.faithlog.billing.service.BillingService;
import com.faithlog.billing.service.BillingQueryService;
import com.faithlog.billing.service.PaymentAccountCommandService;
import com.faithlog.billing.service.query.AdminCampusChargeListQuery;
import com.faithlog.billing.service.query.AdminMemberChargeListQuery;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.controller.dto.response.AdminCampusChargesResponse;
import com.faithlog.billing.controller.dto.response.AdminMemberChargesResponse;
import com.faithlog.billing.controller.dto.request.ChangeChargeStatusRequest;
import com.faithlog.billing.controller.dto.response.ChargeItemResponse;
import com.faithlog.billing.controller.dto.request.CreatePaymentAccountRequest;
import com.faithlog.billing.controller.dto.response.PaymentAccountAdminResponse;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminBillingController {

	private final BillingService billingService;
	private final BillingQueryService billingQueryService;
	private final PaymentAccountCommandService paymentAccountCommandService;

	public AdminBillingController(
		BillingService billingService,
		BillingQueryService billingQueryService,
		PaymentAccountCommandService paymentAccountCommandService
	) {
		this.billingService = billingService;
		this.billingQueryService = billingQueryService;
		this.paymentAccountCommandService = paymentAccountCommandService;
	}

	@PostMapping("/campuses/{campusId}/payment-accounts")
	public ResponseEntity<ApiResponse<PaymentAccountAdminResponse>> createPaymentAccount(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreatePaymentAccountRequest request
	) {
		PaymentAccountResult result = paymentAccountCommandService.createPaymentAccount(
			request.toCommand(campusId, authenticatedUser)
		);
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(PaymentAccountAdminResponse.from(result), "납부 계좌가 등록되었습니다."));
	}

	@PatchMapping("/payment-accounts/{accountId}/deactivate")
	public ApiResponse<PaymentAccountAdminResponse> deactivatePaymentAccount(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long accountId
	) {
		PaymentAccountResult result = paymentAccountCommandService.deactivatePaymentAccount(
			accountId,
			authenticatedUser.userId()
		);
		return ApiResponse.success(PaymentAccountAdminResponse.from(result), "납부 계좌가 비활성화되었습니다.");
	}

	@GetMapping("/campuses/{campusId}/payment-accounts")
	public ApiResponse<List<PaymentAccountAdminResponse>> listAdminPaymentAccounts(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam(required = false) PaymentCategory accountType,
		@RequestParam(defaultValue = "false") boolean includeInactive
	) {
		List<PaymentAccountAdminResponse> responses = billingService
			.listAdminPaymentAccounts(campusId, authenticatedUser.userId(), accountType, includeInactive)
			.stream()
			.map(PaymentAccountAdminResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}

	@PatchMapping("/campuses/{campusId}/payment-accounts/{paymentAccountId}/activate")
	public ApiResponse<PaymentAccountAdminResponse> activatePaymentAccount(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long paymentAccountId
	) {
		PaymentAccountResult result = paymentAccountCommandService.activatePenaltyPaymentAccount(
			campusId,
			paymentAccountId,
			authenticatedUser.userId()
		);
		return ApiResponse.success(PaymentAccountAdminResponse.from(result), "납부 계좌가 활성화되었습니다.");
	}

	@DeleteMapping("/campuses/{campusId}/payment-accounts/{paymentAccountId}")
	public ResponseEntity<Void> deletePaymentAccount(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long paymentAccountId
	) {
		paymentAccountCommandService.deletePaymentAccount(campusId, paymentAccountId, authenticatedUser.userId());
		return ResponseEntity.noContent().build();
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
		@org.springframework.web.bind.annotation.RequestParam(required = false) Long paymentAccountId,
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
				paymentAccountId,
				BillingPageRequests.adminMembers(page, size, sort)
			))
		);
		return ApiResponse.success(response);
	}

	@GetMapping("/campuses/{campusId}/charges/my-accounts")
	public ApiResponse<AdminCampusChargesResponse> listAdminCampusChargesForMyAccounts(
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
			billingQueryService.listAdminCampusChargesForMyAccounts(new AdminCampusChargeListQuery(
				campusId,
				authenticatedUser.userId(),
				paymentCategory,
				status,
				userId,
				keyword,
				null,
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
