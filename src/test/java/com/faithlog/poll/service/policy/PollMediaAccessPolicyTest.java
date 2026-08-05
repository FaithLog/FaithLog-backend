package com.faithlog.poll.service.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.faithlog.poll.infrastructure.repository.PollDocumentRepository;
import com.faithlog.poll.infrastructure.repository.PollImageRepository;
import com.faithlog.poll.service.PollAccessService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PollMediaAccessPolicyTest {

	private static final Instant NOW = Instant.parse("2026-08-04T04:00:00Z");
	@Mock private PollAccessService pollAccessService;
	@Mock private PollImageRepository images;
	@Mock private PollDocumentRepository documents;
	private PollMediaAccessPolicy policy;

	@BeforeEach
	void setUp() {
		policy = new PollMediaAccessPolicy(
			pollAccessService, images, documents, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void readable_attachment_lookup_combines_visible_images_and_pdf_documents() {
		List<Long> assetIds = List.of(41L, 42L);
		when(images.findVisibleAttachedAssetIds(7L, assetIds, NOW, NOW.minusSeconds(3L * 24 * 60 * 60)))
			.thenReturn(List.of(41L));
		when(documents.findVisibleAttachedAssetIds(7L, assetIds, NOW, NOW.minusSeconds(3L * 24 * 60 * 60)))
			.thenReturn(List.of(42L));

		assertThat(policy.readableAttachedAssetIds(7L, 11L, assetIds))
			.containsExactlyInAnyOrder(41L, 42L);
	}
}
