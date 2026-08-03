package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnnouncementQueryServiceTest {

	@Mock
	private AnnouncementRepositoryPort announcementRepository;
	@Mock
	private AnnouncementCategoryRepositoryPort categoryRepository;
	@Mock
	private AnnouncementAccessPolicy accessPolicy;

	private AnnouncementQueryService service;

	@BeforeEach
	void setUp() {
		service = new AnnouncementQueryService(announcementRepository, categoryRepository, accessPolicy);
	}

	@Test
	void published_list_requires_active_member_and_bulk_loads_categories() {
		Announcement first = published(1L, 11L, 101L);
		Announcement second = published(1L, 12L, 102L);
		PageRequest pageable = PageRequest.of(0, 20);
		when(announcementRepository.findByCampusIdAndStatus(1L, AnnouncementStatus.PUBLISHED, pageable))
			.thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
		when(categoryRepository.findByCampusIdAndIdIn(1L, List.of(11L, 12L)))
			.thenReturn(List.of(category(1L, 11L, "일반"), category(1L, 12L, "긴급")));

		var result = service.getAnnouncements(1L, 10L, AnnouncementStatus.PUBLISHED, pageable);

		verify(accessPolicy).requireActiveMember(1L, 10L);
		verify(accessPolicy, never()).requireManager(1L, 10L);
		assertThat(result.getContent()).extracting(item -> item.category().name())
			.containsExactly("일반", "긴급");
	}

	@Test
	void scheduled_and_archived_lists_require_manager() {
		PageRequest pageable = PageRequest.of(0, 20);
		when(announcementRepository.findByCampusIdAndStatus(1L, AnnouncementStatus.SCHEDULED, pageable))
			.thenReturn(new PageImpl<>(List.of(), pageable, 0));

		service.getAnnouncements(1L, 10L, AnnouncementStatus.SCHEDULED, pageable);

		verify(accessPolicy).requireManager(1L, 10L);
		verify(accessPolicy, never()).requireActiveMember(1L, 10L);
	}

	@Test
	void published_detail_preserves_inactive_category_display_data() {
		Announcement announcement = published(1L, 11L, 101L);
		AnnouncementCategory category = category(1L, 11L, "지난 공지");
		category.deactivate();
		when(announcementRepository.findByCampusIdAndId(1L, 101L)).thenReturn(Optional.of(announcement));
		when(categoryRepository.findByCampusIdAndId(1L, 11L)).thenReturn(Optional.of(category));

		var result = service.getAnnouncement(1L, 101L, 10L);

		verify(accessPolicy).requireActiveMember(1L, 10L);
		assertThat(result.category().active()).isFalse();
		assertThat(result.category().name()).isEqualTo("지난 공지");
	}

	private Announcement published(Long campusId, Long categoryId, Long id) {
		Announcement announcement = Announcement.createPublished(
			campusId,
			categoryId,
			20L,
			"공지 " + id,
			"본문",
			false,
			Instant.parse("2026-08-03T03:00:00Z")
		);
		ReflectionTestUtils.setField(announcement, "id", id);
		return announcement;
	}

	private AnnouncementCategory category(Long campusId, Long id, String name) {
		AnnouncementCategory category = AnnouncementCategory.create(campusId, name, "#ABCDEF", 0);
		ReflectionTestUtils.setField(category, "id", id);
		return category;
	}
}
