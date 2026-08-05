package com.faithlog.weeklymaterial.infrastructure.adapter;

import com.faithlog.media.service.port.WeeklyMaterialMediaAccessPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WeeklyMaterialMediaAccessAdapter implements WeeklyMaterialMediaAccessPort {
	private final WeeklyMaterialRepositoryPort materials;

	public WeeklyMaterialMediaAccessAdapter(WeeklyMaterialRepositoryPort materials) {
		this.materials = materials;
	}

	@Override
	public Set<Long> findActiveAttachedAssetIds(Long campusId, List<Long> assetIds) {
		return materials.findActiveAttachedAssetIds(assetIds);
	}
}
