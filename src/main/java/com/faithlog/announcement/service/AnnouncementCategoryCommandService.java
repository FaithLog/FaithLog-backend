package com.faithlog.announcement.service;

import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.service.command.CreateAnnouncementCategoryCommand;
import com.faithlog.announcement.service.command.UpdateAnnouncementCategoryCommand;
import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.announcement.service.result.AnnouncementCategoryResult;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementCategoryCommandService {

	private final AnnouncementCategoryRepositoryPort categoryRepository;
	private final CampusRepositoryPort campusRepository;
	private final AnnouncementAccessPolicy accessPolicy;

	public AnnouncementCategoryCommandService(
		AnnouncementCategoryRepositoryPort categoryRepository,
		CampusRepositoryPort campusRepository,
		AnnouncementAccessPolicy accessPolicy
	) {
		this.categoryRepository = categoryRepository;
		this.campusRepository = campusRepository;
		this.accessPolicy = accessPolicy;
	}

	@Transactional
	public AnnouncementCategoryResult createCategory(CreateAnnouncementCategoryCommand command) {
		accessPolicy.requireManager(command.campusId(), command.requesterId());
		requireCampus(command.campusId());
		AnnouncementCategory category = AnnouncementCategory.create(
			command.campusId(), command.name(), command.color(), command.displayOrder());
		requireUniqueName(command.campusId(), category.name(), null);
		try {
			return AnnouncementCategoryResult.from(categoryRepository.saveAndFlush(category));
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_CATEGORY_DUPLICATE);
		}
	}

	@Transactional
	public AnnouncementCategoryResult updateCategory(UpdateAnnouncementCategoryCommand command) {
		accessPolicy.requireManager(command.campusId(), command.requesterId());
		AnnouncementCategory category = categoryRepository
			.findByCampusIdAndIdForUpdate(command.campusId(), command.categoryId())
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_CATEGORY_NOT_FOUND));
		AnnouncementCategory candidate = AnnouncementCategory.create(
			command.campusId(), command.name(), command.color(), command.displayOrder());
		requireUniqueName(command.campusId(), candidate.name(), command.categoryId());
		category.update(command.name(), command.color(), command.displayOrder());
		try {
			return AnnouncementCategoryResult.from(categoryRepository.saveAndFlush(category));
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_CATEGORY_DUPLICATE);
		}
	}

	@Transactional
	public void deactivateCategory(Long campusId, Long categoryId, Long requesterId) {
		accessPolicy.requireManager(campusId, requesterId);
		AnnouncementCategory category = categoryRepository.findByCampusIdAndIdForUpdate(campusId, categoryId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_CATEGORY_NOT_FOUND));
		category.deactivate();
	}

	private void requireCampus(Long campusId) {
		campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}

	private void requireUniqueName(Long campusId, String name, Long excludedCategoryId) {
		boolean duplicate = excludedCategoryId == null
			? categoryRepository.existsByCampusIdAndNameIgnoreCase(campusId, name)
			: categoryRepository.existsByCampusIdAndNameIgnoreCaseAndIdNot(campusId, name, excludedCategoryId);
		if (duplicate) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_CATEGORY_DUPLICATE);
		}
	}
}
