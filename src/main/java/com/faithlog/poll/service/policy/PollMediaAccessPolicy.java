package com.faithlog.poll.service.policy;

import com.faithlog.poll.infrastructure.repository.PollImageRepository;
import com.faithlog.poll.infrastructure.repository.PollDocumentRepository;
import com.faithlog.poll.service.PollAccessService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PollMediaAccessPolicy {

	private final PollAccessService pollAccessService;
	private final PollImageRepository images;
	private final PollDocumentRepository documents;
	private final Clock clock;

	public PollMediaAccessPolicy(
		PollAccessService pollAccessService,
		PollImageRepository images,
		PollDocumentRepository documents,
		Clock clock
	) {
		this.pollAccessService = pollAccessService;
		this.images = images;
		this.documents = documents;
		this.clock = clock;
	}

	public boolean canUpload(Long campusId, Long requesterId) {
		return pollAccessService.hasAdminVisibility(campusId, requesterId)
			|| pollAccessService.isActiveCoffeeDuty(campusId, requesterId)
			|| pollAccessService.isActiveMealDuty(campusId, requesterId);
	}

	public Set<Long> readableAttachedAssetIds(Long campusId, Long requesterId, List<Long> assetIds) {
		pollAccessService.requirePollReader(campusId, requesterId);
		Instant now = clock.instant();
		HashSet<Long> readable = new HashSet<>(images.findVisibleAttachedAssetIds(
			campusId, assetIds, now, now.minus(Duration.ofDays(3))));
		readable.addAll(documents.findVisibleAttachedAssetIds(
			campusId, assetIds, now, now.minus(Duration.ofDays(3))));
		if (pollAccessService.hasAdminVisibility(campusId, requesterId)) {
			readable.addAll(assetIds);
		} else {
			boolean coffeeDuty = pollAccessService.isActiveCoffeeDuty(campusId, requesterId);
			boolean mealDuty = pollAccessService.isActiveMealDuty(campusId, requesterId);
			if (coffeeDuty || mealDuty) {
				readable.addAll(images.findCreatorAttachedAssetIds(campusId, requesterId, assetIds));
				readable.addAll(documents.findCreatorAttachedAssetIds(campusId, requesterId, assetIds));
			}
			if (mealDuty) {
				readable.addAll(images.findMealDutyManageableAttachedAssetIds(campusId, assetIds));
				readable.addAll(documents.findMealDutyManageableAttachedAssetIds(campusId, assetIds));
			}
		}
		return Set.copyOf(readable);
	}
}
