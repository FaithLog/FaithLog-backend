package com.faithlog.billing.application.policy;

import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;

public final class ChargeStatusPolicy {

	private ChargeStatusPolicy() {
	}

	public static void applyAdminStatusChange(ChargeItem chargeItem, ChargeStatus targetStatus) {
		if (targetStatus == ChargeStatus.PAID) {
			throw new BusinessException(ErrorCode.BILLING_ADMIN_PAID_FORBIDDEN);
		}

		try {
			if (targetStatus == ChargeStatus.WAIVED) {
				chargeItem.waive();
			} else if (targetStatus == ChargeStatus.CANCELED) {
				chargeItem.cancel();
			} else if (targetStatus == ChargeStatus.UNPAID) {
				chargeItem.reopenAsUnpaid();
			} else {
				throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
			}
		} catch (IllegalStateException exception) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_TRANSITION_CONFLICT);
		}
	}
}
