package com.faithlog.poll.infrastructure.adapter;

import com.faithlog.media.service.port.PollMediaAccessPort;
import com.faithlog.poll.service.policy.PollMediaAccessPolicy;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PollMediaAccessAdapter implements PollMediaAccessPort {

	private final PollMediaAccessPolicy accessPolicy;

	public PollMediaAccessAdapter(PollMediaAccessPolicy accessPolicy) {
		this.accessPolicy = accessPolicy;
	}

	@Override
	public boolean canUpload(Long campusId, Long requesterId) {
		return accessPolicy.canUpload(campusId, requesterId);
	}

	@Override
	public Set<Long> readableAttachedAssetIds(Long campusId, Long requesterId, List<Long> assetIds) {
		return accessPolicy.readableAttachedAssetIds(campusId, requesterId, assetIds);
	}
}
