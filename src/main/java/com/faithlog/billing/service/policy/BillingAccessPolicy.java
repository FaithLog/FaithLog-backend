package com.faithlog.billing.service.policy;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;

public final class BillingAccessPolicy {

	private BillingAccessPolicy() {
	}

	public static void requirePaymentAccountManager(CampusMember requesterMembership) {
		requireCampusManager(requesterMembership, ErrorCode.BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN);
	}

	public static void requireChargeStatusManager(CampusMember requesterMembership) {
		requireCampusManager(requesterMembership, ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN);
	}

	public static void requireChargeListManager(CampusMember requesterMembership) {
		requireCampusManager(requesterMembership, ErrorCode.BILLING_CHARGE_LIST_FORBIDDEN);
	}

	public static void requireCampusManager(CampusMember requesterMembership, ErrorCode errorCode, String message) {
		if (!requesterMembership.canManageCampusMembers()) {
			throw new BusinessException(errorCode, message);
		}
	}

	private static void requireCampusManager(CampusMember requesterMembership, ErrorCode errorCode) {
		if (!requesterMembership.canManageCampusMembers()) {
			throw new BusinessException(errorCode);
		}
	}
}
