package com.faithlog.weeklymaterial.infrastructure.adapter;

import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialSlotLockPort;
import org.springframework.stereotype.Component;

@Component
public class WeeklyMaterialSlotLockAdapter implements WeeklyMaterialSlotLockPort {
	private final CampusRepositoryPort campuses;
	public WeeklyMaterialSlotLockAdapter(CampusRepositoryPort campuses) { this.campuses = campuses; }
	@Override
	public void lockCampus(Long campusId) {
		campuses.findByIdForUpdate(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}
}
