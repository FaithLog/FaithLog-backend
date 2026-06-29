package com.faithlog.batch.application;

import com.faithlog.notification.application.NotificationLockKey;
import com.faithlog.notification.application.NotificationLockService;
import com.faithlog.poll.application.CoffeePollSettlementService;
import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.domain.PollOption;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollTemplate;
import com.faithlog.poll.domain.PollTemplateOption;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.infrastructure.jpa.PollOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PollAutomationService {

	public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final PollTemplateRepository pollTemplateRepository;
	private final PollTemplateOptionRepository pollTemplateOptionRepository;
	private final PollRepository pollRepository;
	private final PollOptionRepository pollOptionRepository;
	private final CoffeePollSettlementService coffeePollSettlementService;
	private final NotificationLockService notificationLockService;
	private final TransactionTemplate transactionTemplate;

	public PollAutomationService(
		PollTemplateRepository pollTemplateRepository,
		PollTemplateOptionRepository pollTemplateOptionRepository,
		PollRepository pollRepository,
		PollOptionRepository pollOptionRepository,
		CoffeePollSettlementService coffeePollSettlementService,
		NotificationLockService notificationLockService,
		PlatformTransactionManager transactionManager
	) {
		this.pollTemplateRepository = pollTemplateRepository;
		this.pollTemplateOptionRepository = pollTemplateOptionRepository;
		this.pollRepository = pollRepository;
		this.pollOptionRepository = pollOptionRepository;
		this.coffeePollSettlementService = coffeePollSettlementService;
		this.notificationLockService = notificationLockService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public int createDuePolls(Instant now) {
		int createdCount = 0;
		for (PollTemplate template : pollTemplateRepository.findByIsActiveTrueAndAutoCreateEnabledTrueOrderByIdAsc()) {
			if (createDuePoll(template, now)) {
				createdCount++;
			}
		}
		return createdCount;
	}

	public int closeDueCoffeePolls(Instant now) {
		int closedCount = 0;
		List<Long> duePollIds = pollRepository.findByPollTypeAndStatusAndEndsAtLessThanEqualOrderByIdAsc(
			PollType.COFFEE,
			PollStatus.OPEN,
			now
		).stream().map(Poll::id).toList();
		for (Long pollId : duePollIds) {
			if (closeCoffeePoll(pollId)) {
				closedCount++;
			}
		}
		return closedCount;
	}

	private boolean createDuePoll(PollTemplate template, Instant now) {
		ScheduledPollWindow window = ScheduledPollWindow.from(template, now);
		if (!window.isDue(now)) {
			return false;
		}
		NotificationLockKey lockKey = new NotificationLockKey(
			"poll-auto-create",
			template.campusId(),
			"template:" + template.id() + ":week:" + window.weekStartDate()
		);
		return notificationLockService.acquireScheduledLock(lockKey)
			.map(lease -> {
				try {
					return Boolean.TRUE.equals(transactionTemplate.execute(status -> createIfAbsent(template.id(), window)));
				} finally {
					notificationLockService.release(lease);
				}
			})
			.orElse(false);
	}

	private boolean createIfAbsent(Long templateId, ScheduledPollWindow window) {
		PollTemplate template = pollTemplateRepository.findById(templateId).orElseThrow();
		boolean exists = pollRepository.existsByCampusIdAndTemplateIdAndStartsAtGreaterThanEqualAndStartsAtLessThan(
			template.campusId(),
			template.id(),
			window.weekStartInstant(),
			window.nextWeekStartInstant()
		);
		if (exists) {
			return false;
		}
		List<PollTemplateOption> templateOptions = pollTemplateOptionRepository.findByTemplateIdOrderBySortOrderAsc(template.id());
		if (templateOptions.isEmpty()) {
			return false;
		}

		Poll poll = Poll.create(
			template.campusId(),
			template.id(),
			template.title(),
			template.pollType(),
			template.selectionType(),
			false,
			template.allowUserOptionAdd(),
			template.chargeGenerationType(),
			template.paymentCategory(),
			template.paymentAccountId(),
			window.startsAt(),
			window.endsAt(),
			null
		);
		poll.open();
		Poll savedPoll = pollRepository.save(poll);
		pollOptionRepository.saveAll(templateOptions.stream()
			.map(option -> PollOption.create(
				savedPoll.id(),
				option.content(),
				option.composeMenuCode(),
				option.priceAmount(),
				option.sortOrder()
			))
			.toList());
		return true;
	}

	private boolean closeCoffeePoll(Long pollId) {
		Poll lockScope = pollRepository.findById(pollId).orElseThrow();
		NotificationLockKey lockKey = new NotificationLockKey(
			"coffee-poll-close",
			lockScope.campusId(),
			"poll:" + lockScope.id()
		);
		return notificationLockService.acquireScheduledLock(lockKey)
			.map(lease -> {
				try {
					return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
						Poll poll = pollRepository.findById(pollId).orElseThrow();
						if (poll.status() != PollStatus.OPEN) {
							return false;
						}
						poll.close();
						coffeePollSettlementService.settleClosedCoffeePoll(poll.campusId(), poll.id());
						return true;
					}));
				} finally {
					notificationLockService.release(lease);
				}
			})
			.orElse(false);
	}

	private record ScheduledPollWindow(
		LocalDate weekStartDate,
		Instant weekStartInstant,
		Instant nextWeekStartInstant,
		Instant startsAt,
		Instant endsAt
	) {

		static ScheduledPollWindow from(PollTemplate template, Instant now) {
			LocalDate currentDate = now.atZone(SEOUL_ZONE).toLocalDate();
			LocalDate weekStartDate = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			Instant weekStartInstant = weekStartDate.atStartOfDay(SEOUL_ZONE).toInstant();
			Instant nextWeekStartInstant = weekStartDate.plusWeeks(1).atStartOfDay(SEOUL_ZONE).toInstant();
			Instant startsAt = scheduledInstant(weekStartDate, template.startDayOfWeek(), template.startTime());
			Instant endsAt = scheduledInstant(weekStartDate, template.endDayOfWeek(), template.endTime());
			if (!endsAt.isAfter(startsAt)) {
				endsAt = LocalDateTime.ofInstant(endsAt, SEOUL_ZONE).plusWeeks(1).atZone(SEOUL_ZONE).toInstant();
			}
			return new ScheduledPollWindow(weekStartDate, weekStartInstant, nextWeekStartInstant, startsAt, endsAt);
		}

		private static Instant scheduledInstant(LocalDate weekStartDate, DayOfWeek dayOfWeek, java.time.LocalTime time) {
			return weekStartDate.plusDays(dayOfWeek.getValue() - DayOfWeek.MONDAY.getValue())
				.atTime(time)
				.atZone(SEOUL_ZONE)
				.toInstant();
		}

		private boolean isDue(Instant now) {
			return !now.isBefore(startsAt) && now.isBefore(endsAt);
		}
	}
}
