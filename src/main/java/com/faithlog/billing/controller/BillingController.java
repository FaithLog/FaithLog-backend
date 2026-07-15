package com.faithlog.billing.controller;

import com.faithlog.billing.service.ChargeStatusCommandService;
import com.faithlog.billing.service.MyChargeQueryService;
import com.faithlog.billing.service.PaymentAccountQueryService;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.billing.service.query.MyChargeListQuery;
import com.faithlog.billing.service.query.MyChargeSummaryQuery;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.controller.dto.response.ChargeItemResponse;
import com.faithlog.billing.controller.dto.request.CompleteChargePaymentRequest;
import com.faithlog.billing.controller.dto.response.MyChargeSummaryResponse;
import com.faithlog.billing.controller.dto.response.MyChargesResponse;
import com.faithlog.billing.controller.dto.response.PaymentAccountMemberResponse;
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

	private final MyChargeQueryService myChargeQueryService;
	private final PaymentAccountQueryService paymentAccountQueryService;
	private final ChargeStatusCommandService chargeStatusCommandService;

	public BillingController(
		MyChargeQueryService myChargeQueryService,
		PaymentAccountQueryService paymentAccountQueryService,
		ChargeStatusCommandService chargeStatusCommandService
	) {
		this.myChargeQueryService = myChargeQueryService;
		this.paymentAccountQueryService = paymentAccountQueryService;
		this.chargeStatusCommandService = chargeStatusCommandService;
	}

	@GetMapping("/{campusId}/payment-accounts")
	public ApiResponse<List<PaymentAccountMemberResponse>> listPaymentAccounts(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		List<PaymentAccountMemberResponse> responses = paymentAccountQueryService.listPaymentAccounts(
			campusId,
			authenticatedUser.userId()
		)
			.stream()
			.map(PaymentAccountMemberResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}

	@GetMapping("/{campusId}/charges/me")
	public ApiResponse<MyChargesResponse> listMyCharges(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@org.springframework.web.bind.annotation.RequestParam(required = false) PaymentCategory paymentCategory,
		@org.springframework.web.bind.annotation.RequestParam(required = false) ChargeStatus status,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean includeArchived,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		MyChargesResponse response = MyChargesResponse.from(myChargeQueryService.listMyCharges(new MyChargeListQuery(
			campusId,
			authenticatedUser.userId(),
			paymentCategory,
			status,
			includeArchived,
			BillingPageRequests.chargeItems(page, size, sort)
		)));
		return ApiResponse.success(response);
	}

	@GetMapping("/{campusId}/charges/me/summary")
	public ApiResponse<MyChargeSummaryResponse> getMyChargeSummary(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@org.springframework.web.bind.annotation.RequestParam int year,
		@org.springframework.web.bind.annotation.RequestParam int month
	) {
		MyChargeSummaryResponse response = MyChargeSummaryResponse.from(myChargeQueryService.getMyChargeSummary(
			new MyChargeSummaryQuery(campusId, authenticatedUser.userId(), year, month)
		));
		return ApiResponse.success(response);
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
		ChargeItemResult result = chargeStatusCommandService.completeMyChargePayment(
			CompleteChargePaymentRequest.toCommand(request, campusId, chargeItemId, authenticatedUser)
		);
		return ApiResponse.success(ChargeItemResponse.from(result), "청구가 납부 완료 처리되었습니다.");
	}
}
