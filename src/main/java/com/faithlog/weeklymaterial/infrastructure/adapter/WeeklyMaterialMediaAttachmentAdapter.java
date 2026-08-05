package com.faithlog.weeklymaterial.infrastructure.adapter;

import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WeeklyMaterialMediaAttachmentAdapter implements
	com.faithlog.announcement.service.port.WeeklyMaterialMediaAttachmentPort,
	com.faithlog.poll.service.port.WeeklyMaterialMediaAttachmentPort {
	private final WeeklyMaterialRepositoryPort materials;

	public WeeklyMaterialMediaAttachmentAdapter(WeeklyMaterialRepositoryPort materials) {
		this.materials = materials;
	}

	@Override
	public List<Long> findAttachedAssetIds(List<Long> assetIds) {
		return materials.findAttachedAssetIds(assetIds);
	}
}
