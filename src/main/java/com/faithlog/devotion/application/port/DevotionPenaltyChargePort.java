package com.faithlog.devotion.application.port;

public interface DevotionPenaltyChargePort {

	void requireActivePenaltyAccount(Long campusId);

	void createPenaltyCharge(DevotionPenaltyChargeCommand command);
}
