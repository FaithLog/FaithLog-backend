package com.faithlog.weeklymaterial.service;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialNotificationOutbox;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class WeeklyMaterialFirstPublication {
	private final WeeklyMaterialNotificationOutboxRepositoryPort outboxes;
	public WeeklyMaterialFirstPublication(WeeklyMaterialNotificationOutboxRepositoryPort outboxes) {
		this.outboxes = outboxes;
	}
	public void recordFirstRegistration(WeeklyMaterial material, boolean firstRegistration) {
		if (!firstRegistration || material.materialType() != WeeklyMaterialType.SHARING_SHEET) return;
		outboxes.save(WeeklyMaterialNotificationOutbox.create(material.campusId(), material.id(),
			material.weekStartDate(), material.uploadedBy()));
	}
}
