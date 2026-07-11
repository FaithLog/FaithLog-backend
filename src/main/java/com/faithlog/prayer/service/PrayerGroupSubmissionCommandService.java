package com.faithlog.prayer.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
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
import com.faithlog.prayer.service.command.PrayerSubmissionCommand;
import com.faithlog.prayer.service.command.SavePrayerSubmissionsCommand;
import com.faithlog.prayer.service.result.PrayerWeekBoardResult;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrayerGroupSubmissionCommandService {

	private final PrayerSeasonRepository seasonRepository;
	private final PrayerWeekRepository weekRepository;
	private final PrayerSubmissionRepository submissionRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final PrayerAccessSupport accessSupport;
	private final PrayerTargetMemberSupport targetMemberSupport;
	private final PrayerBoardAssembler boardAssembler;

	public PrayerGroupSubmissionCommandService(
		PrayerSeasonRepository seasonRepository,
		PrayerWeekRepository weekRepository,
		PrayerSubmissionRepository submissionRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		PrayerAccessSupport accessSupport,
		PrayerTargetMemberSupport targetMemberSupport,
		PrayerBoardAssembler boardAssembler
	) {
		this.seasonRepository = seasonRepository;
		this.weekRepository = weekRepository;
		this.submissionRepository = submissionRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.accessSupport = accessSupport;
		this.targetMemberSupport = targetMemberSupport;
		this.boardAssembler = boardAssembler;
	}

	@Transactional
	public PrayerWeekBoardResult saveSubmissions(SavePrayerSubmissionsCommand command) {
		validateWeekStartDate(command.weekStartDate());
		CampusUserLookupResult requester = accessSupport.getActiveUser(command.requesterId(), ErrorCode.AUTH_UNAUTHORIZED);
		PrayerSeason season = getCurrentSeasonOrThrow(command.campusId());
		TargetMembers targetMembers = targetMemberSupport.loadTargetMembers(command.campusId(), season.id());
		requireSubmissionPermission(command.campusId(), requester, command.submissions(), targetMembers);
		Map<Long, PrayerSubmissionCommand> requestedByUserId = requestedSubmissionMap(command.submissions());
		if (!targetMembers.userIds().containsAll(requestedByUserId.keySet())) {
			throw new BusinessException(ErrorCode.PRAYER_MEMBER_NOT_FOUND);
		}

		PrayerWeek existingWeek = weekRepository
			.findByCampusIdAndSeasonIdAndWeekStartDate(command.campusId(), season.id(), command.weekStartDate())
			.orElse(null);
		Map<Long, PrayerSubmission> existingByUserId = existingWeek == null
			? Map.of()
			: submissionRepository.findByPrayerWeekId(existingWeek.id()).stream()
				.collect(Collectors.toMap(PrayerSubmission::userId, Function.identity()));
		validateVersions(requestedByUserId.values(), existingByUserId);

		PrayerWeek week = existingWeek == null
			? weekRepository.save(PrayerWeek.create(command.campusId(), season.id(), command.weekStartDate()))
			: existingWeek;
		Instant now = Instant.now();
		for (PrayerSubmissionCommand submissionCommand : requestedByUserId.values()) {
			PrayerSubmission existing = existingByUserId.get(submissionCommand.userId());
			if (existing == null) {
				submissionRepository.save(PrayerSubmission.create(
					week.id(),
					targetMembers.groupIdByUserId().get(submissionCommand.userId()),
					submissionCommand.userId(),
					submissionCommand.content(),
					requester.userId(),
					now
				));
			} else {
				updateExistingSubmission(existing, submissionCommand, requester.userId(), now);
			}
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

	private void updateExistingSubmission(
		PrayerSubmission existing,
		PrayerSubmissionCommand command,
		Long requesterId,
		Instant submittedAt
	) {
		int updatedRows = submissionRepository.updateContentIfVersionMatches(
			existing.id(),
			command.content(),
			requesterId,
			submittedAt,
			command.version()
		);
		if (updatedRows == 0) {
			throw new BusinessException(ErrorCode.PRAYER_SUBMISSION_CONFLICT);
		}
	}

	private void requireSubmissionPermission(
		Long campusId,
		CampusUserLookupResult requester,
		java.util.List<PrayerSubmissionCommand> submissions,
		TargetMembers targetMembers
	) {
		if (requester.isAdmin() || accessSupport.isCampusManager(campusId, requester.userId())) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_SUBMISSION_FORBIDDEN));
		Long requesterGroupId = targetMembers.groupIdByUserId().get(requesterMembership.userId());
		if (requesterGroupId == null) {
			throw new BusinessException(ErrorCode.PRAYER_SUBMISSION_FORBIDDEN);
		}
		boolean allInRequesterGroup = submissions.stream()
			.allMatch(submission -> requesterGroupId.equals(targetMembers.groupIdByUserId().get(submission.userId())));
		if (!allInRequesterGroup) {
			throw new BusinessException(ErrorCode.PRAYER_SUBMISSION_FORBIDDEN);
		}
	}

	private void validateVersions(
		Collection<PrayerSubmissionCommand> requestedSubmissions,
		Map<Long, PrayerSubmission> existingByUserId
	) {
		for (PrayerSubmissionCommand command : requestedSubmissions) {
			PrayerSubmission existing = existingByUserId.get(command.userId());
			int currentVersion = existing == null ? 0 : existing.version();
			if (command.version() != currentVersion) {
				throw new BusinessException(ErrorCode.PRAYER_SUBMISSION_CONFLICT);
			}
		}
	}

	private Map<Long, PrayerSubmissionCommand> requestedSubmissionMap(
		java.util.List<PrayerSubmissionCommand> submissions
	) {
		if (submissions == null || submissions.isEmpty()) {
			throw new BusinessException(ErrorCode.PRAYER_INVALID_SUBMISSION_REQUEST);
		}
		Map<Long, PrayerSubmissionCommand> requestedByUserId = new LinkedHashMap<>();
		for (PrayerSubmissionCommand submission : submissions) {
			if (submission.userId() == null
				|| submission.version() < 0
				|| requestedByUserId.put(submission.userId(), submission) != null) {
				throw new BusinessException(ErrorCode.PRAYER_INVALID_SUBMISSION_REQUEST);
			}
		}
		return requestedByUserId;
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
