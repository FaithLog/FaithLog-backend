package com.faithlog.poll.application;

import com.faithlog.billing.application.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.campus.application.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import com.faithlog.campus.domain.DutyType;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.domain.PollComment;
import com.faithlog.poll.domain.PollOption;
import com.faithlog.poll.domain.PollResponse;
import com.faithlog.poll.domain.PollResponseOption;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollTemplate;
import com.faithlog.poll.domain.PollTemplateOption;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import com.faithlog.poll.infrastructure.jpa.PollCommentRepository;
import com.faithlog.poll.infrastructure.jpa.PollOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
import com.faithlog.poll.infrastructure.jpa.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollResponseRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollService {

	private final PollRepository pollRepository;
	private final PollOptionRepository pollOptionRepository;
	private final PollResponseRepository pollResponseRepository;
	private final PollResponseOptionRepository pollResponseOptionRepository;
	private final PollCommentRepository pollCommentRepository;
	private final PollTemplateRepository pollTemplateRepository;
	private final PollTemplateOptionRepository pollTemplateOptionRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final PollOptionSnapshotResolver optionSnapshotResolver;
	private final PollAccessService pollAccessService;
	private final CoffeePollSettlementService coffeePollSettlementService;

	public PollService(
		PollRepository pollRepository,
		PollOptionRepository pollOptionRepository,
		PollResponseRepository pollResponseRepository,
		PollResponseOptionRepository pollResponseOptionRepository,
		PollCommentRepository pollCommentRepository,
		PollTemplateRepository pollTemplateRepository,
		PollTemplateOptionRepository pollTemplateOptionRepository,
		PaymentAccountRepositoryPort paymentAccountRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		PollOptionSnapshotResolver optionSnapshotResolver,
		PollAccessService pollAccessService,
		CoffeePollSettlementService coffeePollSettlementService
	) {
		this.pollRepository = pollRepository;
		this.pollOptionRepository = pollOptionRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.pollResponseOptionRepository = pollResponseOptionRepository;
		this.pollCommentRepository = pollCommentRepository;
		this.pollTemplateRepository = pollTemplateRepository;
		this.pollTemplateOptionRepository = pollTemplateOptionRepository;
		this.paymentAccountRepository = paymentAccountRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.optionSnapshotResolver = optionSnapshotResolver;
		this.pollAccessService = pollAccessService;
		this.coffeePollSettlementService = coffeePollSettlementService;
	}

	@Transactional
	public PollResult createPoll(CreatePollCommand command) {
		if (!command.startsAt().isBefore(command.endsAt())) {
			throw new BusinessException(ErrorCode.POLL_INVALID_PERIOD);
		}

		if (command.templateId() != null) {
			return createFromTemplate(command);
		}
		pollAccessService.requirePollCreator(command.campusId(), command.requesterId(), command.pollType());
		return createDirect(command);
	}

	@Transactional
	public List<PollListItemResult> listPolls(Long campusId, Long requesterId) {
		pollAccessService.requirePollReader(campusId, requesterId);
		boolean adminWindow = pollAccessService.hasAdminVisibility(campusId, requesterId);
		List<Poll> campusPolls = pollRepository.findByCampusIdOrderByIdDesc(campusId);
		campusPolls.forEach(this::openScheduledPollIfCurrent);
		List<Poll> visiblePolls = campusPolls
			.stream()
			.filter(poll -> isVisibleInWindow(poll, adminWindow))
			.toList();
		if (visiblePolls.isEmpty()) {
			return List.of();
		}
		Set<Long> respondedPollIds = pollResponseRepository.findByPollIdInAndUserId(
				visiblePolls.stream().map(Poll::id).toList(),
				requesterId
			)
			.stream()
			.map(PollResponse::pollId)
			.collect(HashSet::new, HashSet::add, HashSet::addAll);
		return visiblePolls.stream()
			.map(poll -> PollListItemResult.of(poll, respondedPollIds.contains(poll.id())))
			.toList();
	}

	@Transactional
	public PollResult getPoll(Long campusId, Long pollId, Long requesterId) {
		Poll poll = getVisiblePoll(campusId, pollId, requesterId);
		return toResult(poll);
	}

	@Transactional
	public PollDetailResult getPollDetail(Long campusId, Long pollId, Long requesterId) {
		Poll poll = getVisiblePoll(campusId, pollId, requesterId);
		PollResponseResult myResponse = pollResponseRepository.findByPollIdAndUserId(poll.id(), requesterId)
			.map(response -> PollResponseResult.of(response, optionIdsForResponse(response.id())))
			.orElse(null);
		return new PollDetailResult(toResult(poll), myResponse);
	}

	@Transactional
	public PollResult closePoll(Long campusId, Long pollId, Long requesterId) {
		Poll poll = getPollInCampus(campusId, pollId);
		pollAccessService.requirePollAdmin(campusId, requesterId, poll.pollType());
		if (poll.status() != PollStatus.OPEN) {
			throw new BusinessException(ErrorCode.POLL_CLOSE_NOT_ALLOWED);
		}
		poll.closeAt(Instant.now());
		if (poll.pollType() == PollType.COFFEE) {
			coffeePollSettlementService.settleClosedCoffeePoll(campusId, pollId);
		}
		return toResult(poll);
	}

	@Transactional
	public PollOptionResult addUserOption(AddPollOptionCommand command) {
		pollAccessService.requireActiveCampusMember(command.campusId(), command.requesterId());
		Poll poll = getPollInCampus(command.campusId(), command.pollId());
		requireOpenPoll(poll);
		if (!poll.allowUserOptionAdd()) {
			throw new BusinessException(ErrorCode.POLL_USER_OPTION_ADD_DISABLED);
		}
		List<PollOption> options = pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id());
		int nextSortOrder = options.stream()
			.mapToInt(PollOption::sortOrder)
			.max()
			.orElse(0) + 1;
		PollOptionSnapshot snapshot = optionSnapshotResolver.resolveUserAddedOption(
			poll.pollType(),
			command.content(),
			command.menuId(),
			nextSortOrder
		);
		boolean duplicated = options.stream()
			.map(PollOption::content)
			.anyMatch(existingContent -> existingContent.equalsIgnoreCase(snapshot.content()));
		if (duplicated) {
			throw new BusinessException(ErrorCode.POLL_OPTION_DUPLICATE_CONTENT);
		}
		return PollOptionResult.from(pollOptionRepository.save(PollOption.createUserAdded(
			poll.id(),
			snapshot.content(),
			snapshot.composeMenuCode(),
			snapshot.priceAmount(),
			snapshot.sortOrder(),
			command.requesterId()
		)));
	}

	@Transactional
	public PollResponseResult respondToPoll(RespondToPollCommand command) {
		pollAccessService.requireActiveCampusMember(command.campusId(), command.requesterId());
		Poll poll = getPollInCampus(command.campusId(), command.pollId());
		requireOpenPoll(poll);
		validateSelectionCount(poll.selectionType(), command.optionIds());
		validateNoDuplicateOptions(command.optionIds());
		Map<Long, PollOption> optionsById = optionsById(command.pollId());
		for (Long optionId : command.optionIds()) {
			if (!optionsById.containsKey(optionId)) {
				throw new BusinessException(ErrorCode.POLL_OPTION_NOT_FOUND);
			}
		}

		PollResponse response = pollResponseRepository.findByPollIdAndUserId(poll.id(), command.requesterId())
			.orElseGet(() -> pollResponseRepository.save(PollResponse.create(poll.id(), command.requesterId(), command.memo())));
		response.updateMemo(command.memo());
		pollResponseOptionRepository.deleteByResponseId(response.id());
		pollResponseOptionRepository.saveAll(command.optionIds().stream()
			.map(optionId -> PollResponseOption.create(response.id(), optionId))
			.toList());
		return PollResponseResult.of(response, command.optionIds());
	}

	@Transactional
	public PollResultView getPollResults(Long campusId, Long pollId, Long requesterId) {
		Poll poll = getVisiblePoll(campusId, pollId, requesterId);
		List<PollOption> options = pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id());
		List<PollResponse> responses = pollResponseRepository.findByPollIdOrderByIdAsc(poll.id());
		Map<Long, PollResponse> responsesById = responses.stream()
			.collect(HashMap::new, (map, response) -> map.put(response.id(), response), HashMap::putAll);
		List<PollResponseOption> responseOptions = responses.isEmpty()
			? List.of()
			: pollResponseOptionRepository.findByResponseIdIn(responses.stream().map(PollResponse::id).toList());
		Map<Long, List<PollResponseOption>> byOptionId = new HashMap<>();
		for (PollResponseOption responseOption : responseOptions) {
			byOptionId.computeIfAbsent(responseOption.optionId(), ignored -> new ArrayList<>()).add(responseOption);
		}
		Map<Long, CampusUserLookupResult> usersById = poll.isAnonymous()
			? Map.of()
			: pollAccessService.getUsers(responses.stream().map(PollResponse::userId).toList());
		List<PollOptionResultView> optionResults = options.stream()
			.map(option -> optionResult(poll, option, byOptionId.getOrDefault(option.id(), List.of()), responsesById, usersById))
			.toList();
		long targetMemberCount = campusMemberRepository.countByCampusIdAndStatus(campusId, CampusMemberStatus.ACTIVE);
		long respondedCount = responses.size();
		return new PollResultView(
			poll.id(),
			poll.campusId(),
			poll.title(),
			poll.pollType(),
			poll.selectionType(),
			poll.isAnonymous(),
			poll.status(),
			poll.startsAt(),
			poll.endsAt(),
			targetMemberCount,
			respondedCount,
			targetMemberCount - respondedCount,
			optionResults
		);
	}

	@Transactional(readOnly = true)
	public List<PollMissingMemberResult> getMissingMembers(Long campusId, Long pollId, Long requesterId) {
		Poll poll = getPollInCampus(campusId, pollId);
		pollAccessService.requirePollAdmin(campusId, requesterId, poll.pollType());
		Set<Long> respondedUserIds = pollResponseRepository.findByPollIdOrderByIdAsc(poll.id())
			.stream()
			.map(PollResponse::userId)
			.collect(HashSet::new, HashSet::add, HashSet::addAll);
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE)
			.stream()
			.filter(member -> !respondedUserIds.contains(member.userId()))
			.map(member -> {
				CampusUserLookupResult user = pollAccessService.getUser(member.userId());
				return new PollMissingMemberResult(user.userId(), user.name(), user.email());
			})
			.toList();
	}

	@Transactional
	public List<PollCommentResult> listComments(Long campusId, Long pollId, Long requesterId) {
		getVisiblePoll(campusId, pollId, requesterId);
		return pollCommentRepository.findByPollIdOrderByIdAsc(pollId)
			.stream()
			.map(comment -> PollCommentResult.of(comment, pollAccessService.getUser(comment.userId())))
			.toList();
	}

	@Transactional
	public PollCommentResult createComment(CreatePollCommentCommand command) {
		pollAccessService.requireActiveCampusMember(command.campusId(), command.requesterId());
		Poll poll = getPollInCampus(command.campusId(), command.pollId());
		requireOpenPoll(poll);
		PollComment comment = pollCommentRepository.save(PollComment.create(poll.id(), command.requesterId(), command.content()));
		return PollCommentResult.of(comment, pollAccessService.getUser(command.requesterId()));
	}

	@Transactional
	public PollCommentResult updateComment(UpdatePollCommentCommand command) {
		pollAccessService.requirePollReader(command.campusId(), command.requesterId());
		Poll poll = getPollInCampus(command.campusId(), command.pollId());
		requireOpenPoll(poll);
		PollComment comment = getCommentInPoll(command.pollId(), command.commentId());
		requireCommentEditor(command.campusId(), command.requesterId(), comment);
		comment.update(command.content());
		return PollCommentResult.of(comment, pollAccessService.getUser(comment.userId()));
	}

	@Transactional
	public void deleteComment(DeletePollCommentCommand command) {
		pollAccessService.requirePollReader(command.campusId(), command.requesterId());
		Poll poll = getPollInCampus(command.campusId(), command.pollId());
		requireOpenPoll(poll);
		PollComment comment = getCommentInPoll(command.pollId(), command.commentId());
		requireCommentEditor(command.campusId(), command.requesterId(), comment);
		comment.delete();
	}

	private PollResult createFromTemplate(CreatePollCommand command) {
		PollTemplate template = pollTemplateRepository.findById(command.templateId())
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND));
		if (!template.campusId().equals(command.campusId())) {
			throw new BusinessException(ErrorCode.POLL_TEMPLATE_NOT_FOUND);
		}
		if (!template.isActive()) {
			throw new BusinessException(ErrorCode.POLL_TEMPLATE_INACTIVE);
		}
		pollAccessService.requirePollCreator(command.campusId(), command.requesterId(), template.pollType());
		List<PollTemplateOption> templateOptions = pollTemplateOptionRepository.findByTemplateIdOrderBySortOrderAsc(template.id());
		if (templateOptions.isEmpty()) {
			throw new BusinessException(ErrorCode.POLL_INVALID_OPTION);
		}
		requireCoffeePrerequisitesIfNeeded(
			template.pollType(),
			template.chargeGenerationType(),
			template.paymentCategory(),
			template.paymentAccountId(),
			command.campusId(),
			command.requesterId()
		);
		Poll poll = pollRepository.save(Poll.create(
			command.campusId(),
			template.id(),
			command.title(),
			template.pollType(),
			template.selectionType(),
			command.isAnonymous(),
			template.allowUserOptionAdd(),
			template.chargeGenerationType(),
			template.paymentCategory(),
			template.paymentAccountId(),
			command.startsAt(),
			command.endsAt(),
			command.requesterId()
		));
		openIfCurrent(poll);
		pollOptionRepository.saveAll(templateOptions.stream()
			.map(option -> PollOption.create(
				poll.id(),
				option.content(),
				option.composeMenuCode(),
				option.priceAmount(),
				option.sortOrder()
			))
			.toList());
		return toResult(poll);
	}

	private PollResult createDirect(CreatePollCommand command) {
		PollType pollType = command.pollType();
		if (pollType == null) {
			throw new BusinessException(ErrorCode.POLL_INVALID_OPTION);
		}
		SelectionType selectionType = command.selectionType() == null ? SelectionType.SINGLE : command.selectionType();
		ChargeGenerationType chargeGenerationType = command.chargeGenerationType() == null ? ChargeGenerationType.NONE : command.chargeGenerationType();
		boolean allowUserOptionAdd = command.allowUserOptionAdd() == null
			? pollType == PollType.COFFEE
			: command.allowUserOptionAdd();
		List<PollOptionSnapshot> snapshots = optionSnapshotResolver.resolvePollOptions(command.options());
		requireCoffeePrerequisitesIfNeeded(
			pollType,
			chargeGenerationType,
			command.paymentCategory(),
			command.paymentAccountId(),
			command.campusId(),
			command.requesterId()
		);
		Poll poll = pollRepository.save(Poll.create(
			command.campusId(),
			null,
			command.title(),
			pollType,
			selectionType,
			command.isAnonymous(),
			allowUserOptionAdd,
			chargeGenerationType,
			command.paymentCategory(),
			command.paymentAccountId(),
			command.startsAt(),
			command.endsAt(),
			command.requesterId()
		));
		openIfCurrent(poll);
		pollOptionRepository.saveAll(snapshots.stream()
			.map(snapshot -> PollOption.create(
				poll.id(),
				snapshot.content(),
				snapshot.composeMenuCode(),
				snapshot.priceAmount(),
				snapshot.sortOrder()
			))
			.toList());
		return toResult(poll);
	}

	private void openIfCurrent(Poll poll) {
		Instant now = Instant.now();
		if (!now.isBefore(poll.startsAt()) && now.isBefore(poll.endsAt())) {
			poll.open();
		}
	}

	private void openScheduledPollIfCurrent(Poll poll) {
		Instant now = Instant.now();
		if (poll.status() == PollStatus.SCHEDULED && !now.isBefore(poll.startsAt()) && now.isBefore(poll.endsAt())) {
			poll.open();
		}
	}

	private void requireCoffeePrerequisitesIfNeeded(
		PollType pollType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		Long campusId,
		Long requesterId
	) {
		if (pollType != PollType.COFFEE
			&& chargeGenerationType != ChargeGenerationType.OPTION_PRICE
			&& paymentCategory != PaymentCategory.COFFEE) {
			return;
		}
		if (paymentAccountId == null) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
		PaymentAccount account = paymentAccountRepository.findById(paymentAccountId)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING));
		if (!account.isActive()
			|| !account.campusId().equals(campusId)
			|| account.accountType() != PaymentCategory.COFFEE
			|| !requesterId.equals(account.ownerUserId())) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
	}

	private PollResult toResult(Poll poll) {
		List<PollOptionResult> options = pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id())
			.stream()
			.map(PollOptionResult::from)
			.toList();
		return PollResult.of(poll, options);
	}

	private Poll getVisiblePoll(Long campusId, Long pollId, Long requesterId) {
		pollAccessService.requirePollReader(campusId, requesterId);
		Poll poll = getPollInCampus(campusId, pollId);
		openScheduledPollIfCurrent(poll);
		if (!isVisibleInWindow(poll, pollAccessService.hasAdminVisibility(campusId, requesterId))) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		return poll;
	}

	private Poll getPollInCampus(Long campusId, Long pollId) {
		Poll poll = pollRepository.findById(pollId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
		if (!poll.campusId().equals(campusId)) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		return poll;
	}

	private boolean isVisibleInWindow(Poll poll, boolean adminWindow) {
		Instant now = Instant.now();
		if (poll.status() == PollStatus.OPEN && !now.isBefore(poll.startsAt()) && now.isBefore(poll.endsAt())) {
			return true;
		}
		if (now.isBefore(poll.endsAt())) {
			return false;
		}
		Duration window = adminWindow ? Duration.ofDays(7) : Duration.ofDays(3);
		return !now.isAfter(poll.endsAt().plus(window));
	}

	private void validateSelectionCount(SelectionType selectionType, List<Long> optionIds) {
		if (optionIds == null || optionIds.isEmpty()) {
			throw new BusinessException(ErrorCode.POLL_RESPONSE_INVALID_SELECTION_COUNT);
		}
		if (selectionType == SelectionType.SINGLE && optionIds.size() != 1) {
			throw new BusinessException(ErrorCode.POLL_RESPONSE_INVALID_SELECTION_COUNT);
		}
	}

	private void requireOpenPoll(Poll poll) {
		openScheduledPollIfCurrent(poll);
		Instant now = Instant.now();
		if (poll.status() != PollStatus.OPEN || now.isBefore(poll.startsAt()) || !now.isBefore(poll.endsAt())) {
			throw new BusinessException(ErrorCode.POLL_CLOSED);
		}
	}

	private void validateNoDuplicateOptions(List<Long> optionIds) {
		if (new HashSet<>(optionIds).size() != optionIds.size()) {
			throw new BusinessException(ErrorCode.POLL_RESPONSE_DUPLICATE_OPTION);
		}
	}

	private Map<Long, PollOption> optionsById(Long pollId) {
		Map<Long, PollOption> optionsById = new HashMap<>();
		pollOptionRepository.findByPollIdOrderBySortOrderAsc(pollId)
			.forEach(option -> optionsById.put(option.id(), option));
		return optionsById;
	}

	private List<Long> optionIdsForResponse(Long responseId) {
		return pollResponseOptionRepository.findByResponseIdOrderByIdAsc(responseId)
			.stream()
			.map(PollResponseOption::optionId)
			.toList();
	}

	private PollOptionResultView optionResult(
		Poll poll,
		PollOption option,
		List<PollResponseOption> responseOptions,
		Map<Long, PollResponse> responsesById,
		Map<Long, CampusUserLookupResult> usersById
	) {
		List<PollRespondentResult> respondents = poll.isAnonymous()
			? List.of()
			: responseOptions.stream()
				.map(responseOption -> responsesById.get(responseOption.responseId()))
				.filter(response -> response != null)
				.sorted(Comparator.comparing(PollResponse::id))
				.map(response -> {
					CampusUserLookupResult user = usersById.get(response.userId());
					return new PollRespondentResult(user.userId(), user.name(), user.email());
				})
				.toList();
		return new PollOptionResultView(option.id(), option.content(), option.sortOrder(), responseOptions.size(), respondents);
	}

	private PollComment getCommentInPoll(Long pollId, Long commentId) {
		PollComment comment = pollCommentRepository.findById(commentId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_COMMENT_NOT_FOUND));
		if (!comment.pollId().equals(pollId)) {
			throw new BusinessException(ErrorCode.POLL_COMMENT_NOT_FOUND);
		}
		return comment;
	}

	private void requireCommentEditor(Long campusId, Long requesterId, PollComment comment) {
		if (comment.userId().equals(requesterId) || pollAccessService.hasAdminVisibility(campusId, requesterId)) {
			return;
		}
		throw new BusinessException(ErrorCode.POLL_COMMENT_FORBIDDEN);
	}
}
