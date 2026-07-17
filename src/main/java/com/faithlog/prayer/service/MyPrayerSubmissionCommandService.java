package com.faithlog.prayer.service;

import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.prayer.domain.entity.PrayerSeason;
import com.faithlog.prayer.domain.entity.PrayerSubmission;
import com.faithlog.prayer.domain.entity.PrayerWeek;
import com.faithlog.prayer.domain.type.PrayerSeasonStatus;
import com.faithlog.prayer.infrastructure.repository.PrayerSeasonRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerSubmissionRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerWeekRepository;
import com.faithlog.prayer.service.PrayerTargetMemberSupport.TargetMembers;
import com.faithlog.prayer.service.command.SaveMyPrayerSubmissionCommand;
import com.faithlog.prayer.service.result.PrayerWeekBoardResult;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyPrayerSubmissionCommandService {

	private final PrayerSeasonRepository seasonRepository;
	private final PrayerWeekRepository weekRepository;
	private final PrayerSubmissionRepository submissionRepository;
	private final PrayerAccessSupport accessSupport;
	private final PrayerTargetMemberSupport targetMemberSupport;
	private final PrayerBoardAssembler boardAssembler;

	public MyPrayerSubmissionCommandService(
		PrayerSeasonRepository seasonRepository,
		PrayerWeekRepository weekRepository,
		PrayerSubmissionRepository submissionRepository,
		PrayerAccessSupport accessSupport,
		PrayerTargetMemberSupport targetMemberSupport,
		PrayerBoardAssembler boardAssembler
	) {
		this.seasonRepository = seasonRepository;
		this.weekRepository = weekRepository;
		this.submissionRepository = submissionRepository;
		this.accessSupport = accessSupport;
		this.targetMemberSupport = targetMemberSupport;
		this.boardAssembler = boardAssembler;
	}

	@Transactional
	public PrayerWeekBoardResult saveMySubmission(SaveMyPrayerSubmissionCommand command) {
		validateWeekStartDate(command.weekStartDate());
		CampusUserLookupResult requester = accessSupport.getActiveUser(command.requesterId(), ErrorCode.AUTH_UNAUTHORIZED);
		PrayerSeason season = getCurrentSeasonOrThrow(command.campusId());
		accessSupport.requirePrayerReader(command.campusId(), requester.userId());
		TargetMembers targetMembers = targetMemberSupport.loadTargetMembers(command.campusId(), season.id());
		Long groupId = targetMembers.groupIdByUserId().get(requester.userId());
		if (groupId == null) {
			throw new BusinessException(ErrorCode.PRAYER_GROUP_ASSIGNMENT_REQUIRED);
		}

		PrayerWeek week = weekRepository
			.findByCampusIdAndSeasonIdAndWeekStartDate(command.campusId(), season.id(), command.weekStartDate())
			.orElseGet(() -> weekRepository.save(
				PrayerWeek.create(command.campusId(), season.id(), command.weekStartDate())
			));
		Instant now = Instant.now();
		PrayerSubmission existing = submissionRepository
			.findByPrayerWeekIdAndUserId(week.id(), requester.userId())
			.orElse(null);
		if (existing == null) {
			submissionRepository.save(PrayerSubmission.create(
				week.id(),
				groupId,
				requester.userId(),
				command.content(),
				requester.userId(),
				now
			));
		} else {
			submissionRepository.updateContent(existing.id(), command.content(), requester.userId(), now);
		}
		var submissions = submissionRepository.findByPrayerWeekId(week.id());
		return boardAssembler.buildBoard(
			command.campusId(),
			season,
			command.weekStartDate(),
			week,
			submissions,
			requester
		);
	}

	private PrayerSeason getCurrentSeasonOrThrow(Long campusId) {
		return seasonRepository.findByCampusIdAndStatusAndEndDateIsNull(campusId, PrayerSeasonStatus.ACTIVE)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_ACTIVE_SEASON_NOT_FOUND));
	}

	private void validateWeekStartDate(LocalDate weekStartDate) {
		if (weekStartDate == null || weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new BusinessException(ErrorCode.PRAYER_INVALID_WEEK_START_DATE);
		}
	}
}
