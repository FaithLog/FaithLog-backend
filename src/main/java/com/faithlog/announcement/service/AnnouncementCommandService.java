package com.faithlog.announcement.service;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.service.command.CreateAnnouncementCommand;
import com.faithlog.announcement.service.command.UpdateAnnouncementCommand;
import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementPublishedEventPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import com.faithlog.announcement.service.result.AnnouncementResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementCommandService {

	private final AnnouncementRepositoryPort announcementRepository;
	private final AnnouncementCategoryRepositoryPort categoryRepository;
	private final AnnouncementAccessPolicy accessPolicy;
	private final AnnouncementPublishedEventPort publishedEventPort;
	private final AnnouncementImageAttachmentService imageAttachmentService;
	private final Clock clock;

	@Autowired
	public AnnouncementCommandService(
		AnnouncementRepositoryPort announcementRepository,
		AnnouncementCategoryRepositoryPort categoryRepository,
		AnnouncementAccessPolicy accessPolicy,
		AnnouncementPublishedEventPort publishedEventPort,
		AnnouncementImageAttachmentService imageAttachmentService,
		Clock clock
	) {
		this.announcementRepository = announcementRepository;
		this.categoryRepository = categoryRepository;
		this.accessPolicy = accessPolicy;
		this.publishedEventPort = publishedEventPort;
		this.imageAttachmentService = imageAttachmentService;
		this.clock = clock;
	}

	public AnnouncementCommandService(
		AnnouncementRepositoryPort announcementRepository,
		AnnouncementCategoryRepositoryPort categoryRepository,
		AnnouncementAccessPolicy accessPolicy,
		AnnouncementPublishedEventPort publishedEventPort,
		Clock clock
	) {
		this(announcementRepository, categoryRepository, accessPolicy, publishedEventPort, null, clock);
	}

	@Transactional
	public AnnouncementResult createAnnouncement(CreateAnnouncementCommand command) {
		accessPolicy.requireManager(command.campusId(), command.requesterId());
		AnnouncementCategory category = requireActiveCategory(command.campusId(), command.categoryId());
		Instant now = clock.instant();
		Announcement announcement;
		try {
			announcement = command.publishAt() == null
				? Announcement.createPublished(
					command.campusId(), command.categoryId(), command.requesterId(),
					command.title(), command.content(), command.pinned(), now)
				: Announcement.createScheduled(
					command.campusId(), command.categoryId(), command.requesterId(),
					command.title(), command.content(), command.pinned(), command.publishAt(), now);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
		}
		announcement = announcementRepository.save(announcement);
		if (imageAttachmentService != null) {
			imageAttachmentService.replace(announcement.id(), command.campusId(), command.requesterId(), command.imageAssetIds());
		}
		if (announcement.publishedAt() != null) {
			publishedEventPort.recordPublished(announcement, category);
		}
		return AnnouncementResult.from(announcement, category, imageAssetIds(announcement.id()));
	}

	@Transactional
	public AnnouncementResult updateAnnouncement(UpdateAnnouncementCommand command) {
		accessPolicy.requireManager(command.campusId(), command.requesterId());
		Announcement announcement = requireAnnouncementForUpdate(command.campusId(), command.announcementId());
		AnnouncementCategory category = requireActiveCategory(command.campusId(), command.categoryId());
		try {
			announcement.update(
				command.categoryId(), command.title(), command.content(), command.pinned(), command.publishAt(), clock.instant());
		} catch (IllegalStateException exception) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
		}
		if (imageAttachmentService != null) {
			imageAttachmentService.replace(announcement.id(), command.campusId(), command.requesterId(), command.imageAssetIds());
		}
		return AnnouncementResult.from(announcement, category, imageAssetIds(announcement.id()));
	}

	@Transactional
	public AnnouncementResult publishAnnouncement(Long campusId, Long announcementId, Long requesterId) {
		accessPolicy.requireManager(campusId, requesterId);
		Announcement announcement = requireAnnouncementForUpdate(campusId, announcementId);
		AnnouncementCategory category = requireActiveCategory(campusId, announcement.categoryId());
		try {
			announcement.publish(clock.instant());
		} catch (IllegalStateException exception) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT);
		}
		publishedEventPort.recordPublished(announcement, category);
		return AnnouncementResult.from(announcement, category, imageAssetIds(announcement.id()));
	}

	@Transactional
	public void archiveAnnouncement(Long campusId, Long announcementId, Long requesterId) {
		accessPolicy.requireManager(campusId, requesterId);
		requireAnnouncementForUpdate(campusId, announcementId).archive();
	}

	private Announcement requireAnnouncementForUpdate(Long campusId, Long announcementId) {
		return announcementRepository.findByCampusIdAndIdForUpdate(campusId, announcementId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
	}

	private AnnouncementCategory requireActiveCategory(Long campusId, Long categoryId) {
		AnnouncementCategory category = categoryRepository.findByCampusIdAndId(campusId, categoryId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_CATEGORY_NOT_FOUND));
		if (!category.isActive()) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_CATEGORY_INACTIVE);
		}
		return category;
	}

	private java.util.List<Long> imageAssetIds(Long announcementId) {
		return imageAttachmentService == null ? java.util.List.of()
			: imageAttachmentService.getOrderedAssetIds(announcementId);
	}
}
