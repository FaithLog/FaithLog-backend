package com.faithlog.billing.service.policy;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;

public final class ChargeStatusPolicy {

	private ChargeStatusPolicy() {
	}

	public static void applyAdminStatusChange(ChargeItem chargeItem, ChargeStatus targetStatus) {
		try {
			if (targetStatus == ChargeStatus.PAID) {
				chargeItem.markPaid();
			} else if (targetStatus == ChargeStatus.WAIVED) {
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
