package com.faithlog.devotion.service.port;

public interface DevotionPenaltyChargePort {

	void requireActivePenaltyAccount(Long campusId);

	void createPenaltyCharge(DevotionPenaltyChargeCommand command);
}
