package com.faithlog.billing.controller;

import com.faithlog.billing.controller.dto.request.CreateMealPaymentAccountRequest;
import com.faithlog.billing.controller.dto.response.PaymentAccountAdminResponse;
import com.faithlog.billing.controller.dto.response.AdminCampusChargesResponse;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.service.AdminChargeQueryService;
import com.faithlog.billing.service.query.AdminCampusChargeListQuery;
import com.faithlog.billing.service.MealPaymentAccountService;
import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/meal")
public class MealBillingController {

	private final MealPaymentAccountService mealPaymentAccountService;
	private final AdminChargeQueryService adminChargeQueryService;

	public MealBillingController(
		MealPaymentAccountService mealPaymentAccountService,
		AdminChargeQueryService adminChargeQueryService
	) {
		this.mealPaymentAccountService = mealPaymentAccountService;
		this.adminChargeQueryService = adminChargeQueryService;
	}

	@PostMapping("/payment-accounts")
	public ResponseEntity<ApiResponse<PaymentAccountAdminResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreateMealPaymentAccountRequest request
	) {
		PaymentAccountResult result = mealPaymentAccountService.create(request.toCommand(campusId, authenticatedUser));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(PaymentAccountAdminResponse.from(result), "밥 납부 계좌가 등록되었습니다."));
	}

	@GetMapping("/payment-accounts/me")
	public ApiResponse<List<PaymentAccountAdminResponse>> listMine(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam(defaultValue = "false") boolean includeInactive
	) {
		return ApiResponse.success(mealPaymentAccountService.listMine(
			campusId,
			authenticatedUser.userId(),
			includeInactive
		).stream().map(PaymentAccountAdminResponse::from).toList());
	}

	@PatchMapping("/payment-accounts/{accountId}/deactivate")
	public ApiResponse<PaymentAccountAdminResponse> deactivate(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long accountId
	) {
		return ApiResponse.success(PaymentAccountAdminResponse.from(mealPaymentAccountService.deactivate(
			campusId,
			accountId,
			authenticatedUser.userId()
		)), "밥 납부 계좌가 비활성화되었습니다.");
	}

	@GetMapping("/charges/my-accounts")
	public ApiResponse<AdminCampusChargesResponse> listMyAccountCharges(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam(required = false) ChargeStatus status,
		@RequestParam(required = false) Long userId,
		@RequestParam(required = false) String keyword,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return ApiResponse.success(AdminCampusChargesResponse.from(
			adminChargeQueryService.listMealChargesForMyAccounts(new AdminCampusChargeListQuery(
				campusId,
				authenticatedUser.userId(),
				com.faithlog.billing.domain.type.PaymentCategory.MEAL,
				status,
				userId,
				keyword,
				null,
				BillingPageRequests.adminMembers(page, size, sort)
			))
		));
	}
}
