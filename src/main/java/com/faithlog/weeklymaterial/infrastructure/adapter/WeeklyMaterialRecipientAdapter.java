package com.faithlog.weeklymaterial.infrastructure.adapter;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRecipientPort;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WeeklyMaterialRecipientAdapter implements WeeklyMaterialRecipientPort {
	private final CampusMemberRepository members;
	public WeeklyMaterialRecipientAdapter(CampusMemberRepository members) { this.members = members; }
	@Override
	public List<Long> findActiveMemberUserIds(Long campusId) {
		return members.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE).stream()
			.map(CampusMember::userId).toList();
	}
}
