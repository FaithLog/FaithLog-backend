package com.faithlog.announcement.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.infrastructure.repository.AnnouncementDocumentRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnnouncementMediaAccessAdapterTest {

	@Mock private AnnouncementAccessPolicy accessPolicy;
	@Mock private AnnouncementImageRepository images;
	@Mock private AnnouncementDocumentRepository documents;
	@InjectMocks private AnnouncementMediaAccessAdapter adapter;

	@Test
	void published_attachment_lookup_combines_images_and_pdf_documents() {
		when(images.findPublishedAttachedAssetIds(7L, List.of(31L, 32L))).thenReturn(List.of(31L));
		when(documents.findPublishedAttachedAssetIds(7L, List.of(31L, 32L))).thenReturn(List.of(32L));

		assertThat(adapter.findPublishedAttachedAssetIds(7L, List.of(31L, 32L)))
			.containsExactlyInAnyOrder(31L, 32L);
	}
}
