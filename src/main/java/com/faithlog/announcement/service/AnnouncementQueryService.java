package com.faithlog.announcement.service;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import com.faithlog.announcement.service.result.AnnouncementResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnnouncementQueryService {

	private final AnnouncementRepositoryPort announcementRepository;
	private final AnnouncementCategoryRepositoryPort categoryRepository;
	private final AnnouncementAccessPolicy accessPolicy;

	public AnnouncementQueryService(
		AnnouncementRepositoryPort announcementRepository,
		AnnouncementCategoryRepositoryPort categoryRepository,
		AnnouncementAccessPolicy accessPolicy
	) {
		this.announcementRepository = announcementRepository;
		this.categoryRepository = categoryRepository;
		this.accessPolicy = accessPolicy;
	}

	public Page<AnnouncementResult> getAnnouncements(
		Long campusId,
		Long requesterId,
		AnnouncementStatus status,
		Pageable pageable
	) {
		requireStatusAccess(campusId, requesterId, status);
		Page<Announcement> announcements = announcementRepository.findByCampusIdAndStatus(campusId, status, pageable);
		Map<Long, AnnouncementCategory> categories = categoriesById(campusId, announcements.getContent());
		return announcements.map(announcement -> AnnouncementResult.from(
			announcement,
			requireCategory(categories, announcement.categoryId())
		));
	}

	public AnnouncementResult getAnnouncement(Long campusId, Long announcementId, Long requesterId) {
		Announcement announcement = announcementRepository.findByCampusIdAndId(campusId, announcementId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
		requireStatusAccess(campusId, requesterId, announcement.status());
		AnnouncementCategory category = categoryRepository.findByCampusIdAndId(campusId, announcement.categoryId())
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_CATEGORY_NOT_FOUND));
		return AnnouncementResult.from(announcement, category);
	}

	private void requireStatusAccess(Long campusId, Long requesterId, AnnouncementStatus status) {
		if (status == AnnouncementStatus.PUBLISHED) {
			accessPolicy.requireActiveMember(campusId, requesterId);
			return;
		}
		accessPolicy.requireManager(campusId, requesterId);
	}

	private Map<Long, AnnouncementCategory> categoriesById(Long campusId, List<Announcement> announcements) {
		List<Long> categoryIds = announcements.stream()
			.map(Announcement::categoryId)
			.collect(java.util.stream.Collectors.collectingAndThen(
				java.util.stream.Collectors.toCollection(LinkedHashSet::new),
				List::copyOf
			));
		if (categoryIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, AnnouncementCategory> categories = new LinkedHashMap<>();
		categoryRepository.findByCampusIdAndIdIn(campusId, categoryIds)
			.forEach(category -> categories.put(category.id(), category));
		return categories;
	}

	private AnnouncementCategory requireCategory(Map<Long, AnnouncementCategory> categories, Long categoryId) {
		AnnouncementCategory category = categories.get(categoryId);
		if (category == null) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_CATEGORY_NOT_FOUND);
		}
		return category;
	}
}
