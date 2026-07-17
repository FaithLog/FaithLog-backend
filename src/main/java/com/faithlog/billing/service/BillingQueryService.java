package com.faithlog.billing.service;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.query.AdminCampusChargeListQuery;
import com.faithlog.billing.service.query.AdminMemberChargeListQuery;
import com.faithlog.billing.service.query.MyChargeListQuery;
import com.faithlog.billing.service.query.MyChargeSummaryQuery;
import com.faithlog.billing.service.result.AdminCampusChargesResult;
import com.faithlog.billing.service.result.AdminMemberChargesResult;
import com.faithlog.billing.service.result.MyChargeSummaryResult;
import com.faithlog.billing.service.result.MyChargesResult;
import com.faithlog.billing.service.result.PaymentAccountResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BillingQueryService {

	private final MyChargeQueryService myChargeQueryService;
	private final AdminChargeQueryService adminChargeQueryService;
	private final PaymentAccountQueryService paymentAccountQueryService;

	public BillingQueryService(
		MyChargeQueryService myChargeQueryService,
		AdminChargeQueryService adminChargeQueryService,
		PaymentAccountQueryService paymentAccountQueryService
	) {
		this.myChargeQueryService = myChargeQueryService;
		this.adminChargeQueryService = adminChargeQueryService;
		this.paymentAccountQueryService = paymentAccountQueryService;
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

	public MyChargesResult listMyCharges(MyChargeListQuery query) {
		return myChargeQueryService.listMyCharges(query);
	}

	public MyChargeSummaryResult getMyChargeSummary(MyChargeSummaryQuery query) {
		return myChargeQueryService.getMyChargeSummary(query);
	}

	public AdminCampusChargesResult listAdminCampusCharges(AdminCampusChargeListQuery query) {
		return adminChargeQueryService.listAdminCampusCharges(query);
	}

	public AdminCampusChargesResult listAdminCampusChargesForMyAccounts(AdminCampusChargeListQuery query) {
		return adminChargeQueryService.listAdminCampusChargesForMyAccounts(query);
	}

	public AdminMemberChargesResult listAdminMemberCharges(AdminMemberChargeListQuery query) {
		return adminChargeQueryService.listAdminMemberCharges(query);
	}
}
