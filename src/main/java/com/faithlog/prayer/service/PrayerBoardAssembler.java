package com.faithlog.prayer.service;

import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.prayer.domain.entity.PrayerGroupMember;
import com.faithlog.prayer.domain.entity.PrayerSeason;
import com.faithlog.prayer.domain.entity.PrayerSubmission;
import com.faithlog.prayer.domain.entity.PrayerWeek;
import com.faithlog.prayer.domain.type.PrayerWeekStatus;
import com.faithlog.prayer.service.PrayerTargetMemberSupport.TargetMembers;
import com.faithlog.prayer.service.result.PrayerGroupBoardResult;
import com.faithlog.prayer.service.result.PrayerMemberSubmissionResult;
import com.faithlog.prayer.service.result.PrayerSeasonResult;
import com.faithlog.prayer.service.result.PrayerWeekBoardResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class PrayerBoardAssembler {

	private final PrayerTargetMemberSupport targetMemberSupport;
	private final PrayerAccessSupport accessSupport;

	PrayerBoardAssembler(
		PrayerTargetMemberSupport targetMemberSupport,
		PrayerAccessSupport accessSupport
	) {
		this.targetMemberSupport = targetMemberSupport;
		this.accessSupport = accessSupport;
	}

	PrayerWeekBoardResult buildBoard(
		Long campusId,
		PrayerSeason season,
		LocalDate weekStartDate,
		PrayerWeek week,
		List<PrayerSubmission> submissions,
		CampusUserLookupResult requester
	) {
		TargetMembers targetMembers = targetMemberSupport.loadTargetMembers(campusId, season.id());
		Map<Long, PrayerSubmission> submissionsByUserId = submissions.stream()
			.collect(Collectors.toMap(PrayerSubmission::userId, Function.identity(), (left, right) -> left));
		Map<Long, CampusUserLookupResult> usersById = accessSupport.campusUsersById(targetMembers.userIds());
		Long myGroupId = targetMembers.groupIdByUserId().get(requester.userId());
		boolean canEditAll = requester.isAdmin() || accessSupport.isCampusManager(campusId, requester.userId());
		List<PrayerGroupBoardResult> groups = targetMembers.groups().stream()
			.map(group -> {
				List<PrayerMemberSubmissionResult> members = targetMembers.membersByGroupId().getOrDefault(group.id(), List.of())
					.stream()
					.map(member -> toMemberSubmission(
						member,
						usersById.get(member.userId()),
						submissionsByUserId.get(member.userId()),
						canEditAll,
						requester.userId(),
						myGroupId
					))
					.toList();
				return new PrayerGroupBoardResult(group.id(), group.seasonId(), group.name(), group.sortOrder(), members);
			})
			.toList();
		long targetMemberCount = groups.stream()
			.mapToLong(group -> group.members().size())
			.sum();
		long submittedCount = groups.stream()
			.flatMap(group -> group.members().stream())
			.filter(member -> member.submittedAt() != null)
			.count();
		return new PrayerWeekBoardResult(
			campusId,
			weekStartDate,
			weekStartDate.plusDays(6),
			PrayerSeasonResult.from(season),
			myGroupId,
			week == null ? PrayerWeekStatus.OPEN.name() : week.status().name(),
			submittedCount,
			targetMemberCount,
			groups
		);
	}

	private PrayerMemberSubmissionResult toMemberSubmission(
		PrayerGroupMember member,
		CampusUserLookupResult user,
		PrayerSubmission submission,
		boolean canEditAll,
		Long requesterId,
		Long requesterGroupId
	) {
		boolean editable = canEditAll || (member.userId().equals(requesterId) && member.groupId().equals(requesterGroupId));
		if (submission == null) {
			return new PrayerMemberSubmissionResult(user.userId(), user.name(), null, null, false, editable, 0, null);
		}
		return new PrayerMemberSubmissionResult(
			user.userId(),
			user.name(),
			submission.id(),
			submission.content(),
			submission.submittedAt() != null,
			editable,
			submission.version(),
			submission.submittedAt()
		);
	}
}
