package com.faithlog.campus.service;

import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.command.AssignMealDutyCommand;
import com.faithlog.campus.service.command.ChangeCampusRoleCommand;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.service.command.UpdateCampusCommand;
import com.faithlog.campus.service.result.AdminCampusMemberResult;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.result.CampusDetailResult;
import com.faithlog.campus.service.result.CampusMembershipResult;
import com.faithlog.campus.service.result.DutyAssignmentResult;
import com.faithlog.campus.service.result.MyDutyAssignmentResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CampusService {

	private final CampusCreationService campusCreationService;
	private final CampusJoinService campusJoinService;
	private final CampusQueryService campusQueryService;
	private final CampusUpdateService campusUpdateService;
	private final CampusMemberManagementService campusMemberManagementService;
	private final CampusDutyAssignmentService campusDutyAssignmentService;

	public CampusService(
		CampusCreationService campusCreationService,
		CampusJoinService campusJoinService,
		CampusQueryService campusQueryService,
		CampusUpdateService campusUpdateService,
		CampusMemberManagementService campusMemberManagementService,
		CampusDutyAssignmentService campusDutyAssignmentService
	) {
		this.campusCreationService = campusCreationService;
		this.campusJoinService = campusJoinService;
		this.campusQueryService = campusQueryService;
		this.campusUpdateService = campusUpdateService;
		this.campusMemberManagementService = campusMemberManagementService;
		this.campusDutyAssignmentService = campusDutyAssignmentService;
	}

	public CampusCreateResult createCampus(CreateCampusCommand command) {
		return campusCreationService.createCampus(command);
	}

	public CampusMembershipResult joinCampus(JoinCampusCommand command) {
		return campusJoinService.joinCampus(command);
	}

	public void deleteCampusMember(Long campusId, Long membershipId, Long requesterId) {
		campusMemberManagementService.deleteCampusMember(campusId, membershipId, requesterId);
	}

	public AdminCampusMemberResult changeCampusRole(ChangeCampusRoleCommand command) {
		return campusMemberManagementService.changeCampusRole(command);
	}

	public List<AdminCampusMemberResult> getCampusMembers(Long campusId, Long requesterId) {
		return campusMemberManagementService.getCampusMembers(campusId, requesterId);
	}

	public List<DutyAssignmentResult> getDutyAssignments(Long campusId, Long requesterId) {
		return campusDutyAssignmentService.getDutyAssignments(campusId, requesterId);
	}

	public MyDutyAssignmentResult getMyCoffeeDutyAssignment(Long campusId, Long requesterId) {
		return campusDutyAssignmentService.getMyCoffeeDutyAssignment(campusId, requesterId);
	}

	public MyDutyAssignmentResult getMyMealDutyAssignment(Long campusId, Long requesterId) {
		return campusDutyAssignmentService.getMyMealDutyAssignment(campusId, requesterId);
	}

	public DutyAssignmentResult assignCoffeeDuty(AssignCoffeeDutyCommand command) {
		return campusDutyAssignmentService.assignCoffeeDuty(command);
	}

	public DutyAssignmentResult assignMealDuty(AssignMealDutyCommand command) {
		return campusDutyAssignmentService.assignMealDuty(command);
	}

	public void revokeCoffeeDuty(Long campusId, Long assignmentId, Long requesterId) {
		campusDutyAssignmentService.revokeCoffeeDuty(campusId, assignmentId, requesterId);
	}

	public void revokeMealDuty(Long campusId, Long assignmentId, Long requesterId) {
		campusDutyAssignmentService.revokeMealDuty(campusId, assignmentId, requesterId);
	}

	public List<CampusMembershipResult> getMyCampuses(Long requesterId) {
		return campusQueryService.getMyCampuses(requesterId);
	}

	public CampusDetailResult getCampus(Long campusId, Long requesterId) {
		return campusQueryService.getCampus(campusId, requesterId);
	}

	public CampusDetailResult updateCampus(UpdateCampusCommand command) {
		return campusUpdateService.updateCampus(command);
	}

}
