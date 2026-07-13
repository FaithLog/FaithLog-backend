package com.faithlog.billing.service.port;

public interface DevotionChargeReopenPort {

	void reopenWeeklyDevotion(Long campusId, Long userId, Long weeklyRecordId);
}
