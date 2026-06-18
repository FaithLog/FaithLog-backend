package com.faithlog.billing.presentation;

import com.faithlog.billing.application.BillingService;
import com.faithlog.billing.application.BillingQueryService;
import com.faithlog.billing.application.ChargeItemResult;
import com.faithlog.billing.application.MyChargeListQuery;
import com.faithlog.billing.application.MyChargeSummaryQuery;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.billing.presentation.dto.ChargeItemResponse;
import com.faithlog.billing.presentation.dto.CompleteChargePaymentRequest;
import com.faithlog.billing.presentation.dto.MyChargeSummaryResponse;
import com.faithlog.billing.presentation.dto.MyChargesResponse;
import com.faithlog.billing.presentation.dto.PaymentAccountMemberResponse;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
	private final BillingQueryService billingQueryService;

	public BillingController(BillingService billingService, BillingQueryService billingQueryService) {
		this.billingService = billingService;
		this.billingQueryService = billingQueryService;
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

	@GetMapping("/{campusId}/charges/me")
	public ApiResponse<MyChargesResponse> listMyCharges(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@org.springframework.web.bind.annotation.RequestParam(required = false) PaymentCategory paymentCategory,
		@org.springframework.web.bind.annotation.RequestParam(required = false) ChargeStatus status,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		MyChargesResponse response = MyChargesResponse.from(billingQueryService.listMyCharges(new MyChargeListQuery(
			campusId,
			authenticatedUser.userId(),
			paymentCategory,
			status,
			pageable(page, size, sort)
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
		MyChargeSummaryResponse response = MyChargeSummaryResponse.from(billingQueryService.getMyChargeSummary(
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
		ChargeItemResult result = billingService.completeMyChargePayment(
			CompleteChargePaymentRequest.toCommand(request, campusId, chargeItemId, authenticatedUser)
		);
		return ApiResponse.success(ChargeItemResponse.from(result), "청구가 납부 완료 처리되었습니다.");
	}

	private Pageable pageable(int page, int size, String sort) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		return PageRequest.of(safePage, safeSize, sort(sort));
	}

	private Sort sort(String sort) {
		String sortValue = sort == null || sort.isBlank() ? "createdAt,desc" : sort;
		String[] tokens = sortValue.split(",");
		String property = tokens[0].trim();
		if (!List.of("createdAt", "dueDate", "paidAt", "amount", "status", "paymentCategory").contains(property)) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 정렬 기준입니다.");
		}
		Sort.Direction direction = tokens.length > 1 && "asc".equalsIgnoreCase(tokens[1].trim())
			? Sort.Direction.ASC
			: Sort.Direction.DESC;
		return Sort.by(direction, property);
	}
}
