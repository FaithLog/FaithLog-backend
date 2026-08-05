package com.faithlog.weeklymaterial.service;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialNotificationOutbox;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WeeklyMaterialFirstPublication {
	private final WeeklyMaterialNotificationOutboxRepositoryPort outboxes;
	private final Clock clock;
	@Autowired
	public WeeklyMaterialFirstPublication(WeeklyMaterialNotificationOutboxRepositoryPort outboxes, Clock clock) {
		this.outboxes = outboxes;
		this.clock = clock;
	}
	public WeeklyMaterialFirstPublication(WeeklyMaterialNotificationOutboxRepositoryPort outboxes) {
		this(outboxes, Clock.systemUTC());
	}
	public void recordFirstRegistration(WeeklyMaterial material, boolean firstRegistration) {
		if (!firstRegistration || material.materialType() != WeeklyMaterialType.SHARING_SHEET) return;
		if (outboxes.findSlotForUpdate(material.campusId(), material.weekStartDate(), material.materialType())
			.isPresent()) return;
		outboxes.save(WeeklyMaterialNotificationOutbox.create(material.campusId(), material.id(),
			material.weekStartDate(), material.uploadedBy()));
	}

	public void suppressPending(WeeklyMaterial material) {
		outboxes.findSlotForUpdate(material.campusId(), material.weekStartDate(), material.materialType())
			.filter(outbox -> !outbox.isProcessed())
			.ifPresent(outbox -> outbox.markProcessed(clock.instant()));
	}
}
