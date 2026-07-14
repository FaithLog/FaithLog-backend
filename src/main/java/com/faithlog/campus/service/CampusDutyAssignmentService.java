package com.faithlog.campus.service;

import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.command.AssignMealDutyCommand;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.result.DutyAssignmentResult;
import com.faithlog.campus.service.result.MyDutyAssignmentResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusDutyAssignmentService {

	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final CampusAccessPolicy campusAccessPolicy;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final ChargeItemRepositoryPort chargeItemRepository;

	public CampusDutyAssignmentService(
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		CampusAccessPolicy campusAccessPolicy,
		PaymentAccountRepositoryPort paymentAccountRepository,
		ChargeItemRepositoryPort chargeItemRepository
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.campusAccessPolicy = campusAccessPolicy;
		this.paymentAccountRepository = paymentAccountRepository;
		this.chargeItemRepository = chargeItemRepository;
	}

	@Transactional(readOnly = true)
	public List<DutyAssignmentResult> getDutyAssignments(Long campusId, Long requesterId) {
		campusAccessPolicy.requireCampusManager(
			campusId,
			requesterId,
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"커피 담당자 관리 권한이 없습니다."
		);
		return dutyAssignmentRepository.findByCampusIdAndIsActiveTrueOrderByIdAsc(campusId)
			.stream()
			.map(assignment -> DutyAssignmentResult.of(
				assignment,
				campusAccessPolicy.getUserOrThrow(assignment.userId())
			))
			.toList();
	}

	@Transactional(readOnly = true)
	public MyDutyAssignmentResult getMyCoffeeDutyAssignment(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUser(requesterId);
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_VIEW_FORBIDDEN));
		boolean active = dutyAssignmentRepository
			.findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(campusId, DutyType.COFFEE, requester.userId())
			.isPresent();
		return new MyDutyAssignmentResult(requester.userId(), campusId, DutyType.COFFEE.name(), active);
	}

	@Transactional(readOnly = true)
	public MyDutyAssignmentResult getMyMealDutyAssignment(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUser(requesterId);
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_VIEW_FORBIDDEN));
		boolean active = dutyAssignmentRepository
			.findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(campusId, DutyType.MEAL, requester.userId())
			.isPresent();
		return new MyDutyAssignmentResult(requester.userId(), campusId, DutyType.MEAL.name(), active);
	}

	@Transactional
	public DutyAssignmentResult assignCoffeeDuty(AssignCoffeeDutyCommand command) {
		campusAccessPolicy.requireCampusManager(
			command.campusId(),
			command.requesterId(),
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"커피 담당자 관리 권한이 없습니다."
		);
		campusRepository.findByIdForUpdate(command.campusId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		CampusMember targetMember = campusMemberRepository
			.findByCampusIdAndUserId(command.campusId(), command.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CAMPUS_MEMBER_NOT_FOUND,
				"커피 담당자로 지정할 캠퍼스 멤버를 찾을 수 없습니다."
			));

		CampusDutyAssignment assignment = dutyAssignmentRepository
			.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
				command.campusId(), DutyType.COFFEE, targetMember.userId())
			.orElseGet(() -> dutyAssignmentRepository.save(
				CampusDutyAssignment.assignCoffee(command.campusId(), targetMember.userId())
			));
		return DutyAssignmentResult.of(
			assignment,
			campusAccessPolicy.getUserOrThrow(assignment.userId())
		);
	}

	@Transactional
	public DutyAssignmentResult assignMealDuty(AssignMealDutyCommand command) {
		campusAccessPolicy.requireCampusManager(
			command.campusId(),
			command.requesterId(),
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"밥 담당자 관리 권한이 없습니다."
		);
		campusRepository.findByIdForUpdate(command.campusId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		CampusMember targetMember = campusMemberRepository
			.findByCampusIdAndUserId(command.campusId(), command.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CAMPUS_MEMBER_NOT_FOUND,
				"밥 담당자로 지정할 캠퍼스 멤버를 찾을 수 없습니다."
			));

		CampusDutyAssignment assignment = dutyAssignmentRepository
			.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
				command.campusId(), DutyType.MEAL, targetMember.userId())
			.orElseGet(() -> dutyAssignmentRepository.save(
				CampusDutyAssignment.assignMeal(command.campusId(), targetMember.userId())
			));
		return DutyAssignmentResult.of(assignment, campusAccessPolicy.getUserOrThrow(assignment.userId()));
	}

	@Transactional
	public void revokeCoffeeDuty(Long campusId, Long assignmentId, Long requesterId) {
		campusAccessPolicy.requireCampusManager(
			campusId,
			requesterId,
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"커피 담당자 관리 권한이 없습니다."
		);
		campusRepository.findByIdForUpdate(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		CampusDutyAssignment assignment = dutyAssignmentRepository
			.findByCampusIdAndDutyTypeAndId(campusId, DutyType.COFFEE, assignmentId)
			.filter(CampusDutyAssignment::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_DUTY_ASSIGNMENT_NOT_FOUND));
		assignment = dutyAssignmentRepository.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
			campusId, DutyType.COFFEE, assignment.userId())
			.filter(activeAssignment -> activeAssignment.id().equals(assignmentId))
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_DUTY_ASSIGNMENT_NOT_FOUND));
		requireNoOwnedUnpaidCharges(assignment, PaymentCategory.COFFEE, ErrorCode.CAMPUS_COFFEE_DUTY_UNPAID_CHARGE_CONFLICT);
		assignment.revoke();
	}

	@Transactional
	public void revokeMealDuty(Long campusId, Long assignmentId, Long requesterId) {
		campusAccessPolicy.requireCampusManager(
			campusId,
			requesterId,
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"밥 담당자 관리 권한이 없습니다."
		);
		campusRepository.findByIdForUpdate(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		CampusDutyAssignment assignment = dutyAssignmentRepository
			.findByCampusIdAndDutyTypeAndId(campusId, DutyType.MEAL, assignmentId)
			.filter(CampusDutyAssignment::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_DUTY_ASSIGNMENT_NOT_FOUND));
		assignment = dutyAssignmentRepository.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
			campusId, DutyType.MEAL, assignment.userId())
			.filter(activeAssignment -> activeAssignment.id().equals(assignmentId))
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_DUTY_ASSIGNMENT_NOT_FOUND));
		requireNoOwnedUnpaidCharges(assignment, PaymentCategory.MEAL, ErrorCode.CAMPUS_MEAL_DUTY_UNPAID_CHARGE_CONFLICT);
		assignment.revoke();
	}

	private void requireNoOwnedUnpaidCharges(
		CampusDutyAssignment assignment,
		PaymentCategory paymentCategory,
		ErrorCode errorCode
	) {
		Set<Long> ownedAccountIds = paymentAccountRepository
			.findByCampusIdAndOwnerUserIdAndAccountTypeOrderByIdAsc(
				assignment.campusId(), assignment.userId(), paymentCategory)
			.stream()
			.map(account -> account.id())
			.collect(Collectors.toSet());
		if (ownedAccountIds.isEmpty()) {
			return;
		}
		boolean hasUnpaidCharge = chargeItemRepository
			.findByCampusIdAndPaymentCategoryAndStatusAndPaymentAccountIdInOrderByIdAsc(
				assignment.campusId(), paymentCategory, ChargeStatus.UNPAID, ownedAccountIds)
			.stream()
			.findAny()
			.isPresent();
		if (hasUnpaidCharge) {
			throw new BusinessException(errorCode);
		}
	}
}
