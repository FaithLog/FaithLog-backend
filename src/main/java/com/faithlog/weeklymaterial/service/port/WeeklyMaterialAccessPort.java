package com.faithlog.weeklymaterial.service.port;

public interface WeeklyMaterialAccessPort {
	void requireManager(Long campusId, Long requesterId);
	void requireActiveMember(Long campusId, Long requesterId);
}
