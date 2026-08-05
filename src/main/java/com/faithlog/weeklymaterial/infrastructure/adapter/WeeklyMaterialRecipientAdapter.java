package com.faithlog.weeklymaterial.infrastructure.adapter;

import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRecipient;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRecipientPort;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WeeklyMaterialRecipientAdapter implements WeeklyMaterialRecipientPort {
	private final EntityManager entityManager;
	public WeeklyMaterialRecipientAdapter(EntityManager entityManager) { this.entityManager = entityManager; }
	@Override
	public List<WeeklyMaterialRecipient> findAllActiveRecipients() {
		return entityManager.createQuery("""
			select distinct member.userId, min(member.campusId)
			from CampusMember member
			where member.status = com.faithlog.campus.domain.type.CampusMemberStatus.ACTIVE
			group by member.userId
			order by member.userId
			""", Object[].class).getResultList().stream()
			.map(row -> new WeeklyMaterialRecipient((Long) row[0], (Long) row[1]))
			.toList();
	}
}
