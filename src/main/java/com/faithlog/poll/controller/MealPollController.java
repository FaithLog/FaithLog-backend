package com.faithlog.poll.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.response.PageResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.controller.dto.request.CreateMealPollRequest;
import com.faithlog.poll.controller.dto.request.CreateMealPollChargesRequest;
import com.faithlog.poll.controller.dto.response.MealPollSettlementResponse;
import com.faithlog.poll.controller.dto.response.MealPollManagementDetailResponse;
import com.faithlog.poll.controller.dto.response.MealPollManagementListItemResponse;
import com.faithlog.poll.controller.dto.response.PollResponse;
import com.faithlog.poll.service.MealPollService;
import com.faithlog.poll.service.MealPollSettlementService;
import com.faithlog.poll.service.MealPollManagementQueryService;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/meal/polls")
public class MealPollController {

	private final MealPollService mealPollService;
	private final MealPollSettlementService mealPollSettlementService;
	private final MealPollManagementQueryService mealPollManagementQueryService;

	public MealPollController(
		MealPollService mealPollService,
		MealPollSettlementService mealPollSettlementService,
		MealPollManagementQueryService mealPollManagementQueryService
	) {
		this.mealPollService = mealPollService;
		this.mealPollSettlementService = mealPollSettlementService;
		this.mealPollManagementQueryService = mealPollManagementQueryService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<PollResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreateMealPollRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			PollResponse.from(mealPollService.create(request.toCommand(campusId, authenticatedUser))),
			"밥 투표가 생성되었습니다."
		));
	}

	@PatchMapping("/{pollId}/close")
	public ApiResponse<PollResponse> close(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId
	) {
		return ApiResponse.success(PollResponse.from(mealPollService.close(
			campusId,
			pollId,
			authenticatedUser.userId()
		)));
	}

	@PostMapping("/{pollId}/charges")
	public ApiResponse<MealPollSettlementResponse> createCharges(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId,
		@Valid @RequestBody CreateMealPollChargesRequest request
	) {
		return ApiResponse.success(MealPollSettlementResponse.from(mealPollSettlementService.settle(
			request.toCommand(campusId, pollId, authenticatedUser)
		)));
	}

	@GetMapping
	public ApiResponse<PageResponse<MealPollManagementListItemResponse>> list(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam(required = false) PollStatus status,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return ApiResponse.success(PageResponse.from(mealPollManagementQueryService.list(
			campusId, authenticatedUser.userId(), status, pageable(page, size, sort)
		).map(MealPollManagementListItemResponse::from)));
	}

	@GetMapping("/{pollId}")
	public ApiResponse<MealPollManagementDetailResponse> detail(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long pollId
	) {
		return ApiResponse.success(MealPollManagementDetailResponse.from(
			mealPollManagementQueryService.detail(campusId, pollId, authenticatedUser.userId())
		));
	}

	private PageRequest pageable(int page, int size, String rawSort) {
		if (page < 0 || size < 1 || size > 100) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
		}
		String[] tokens = rawSort.split(",", -1);
		if (tokens.length != 2 || !java.util.Set.of("id", "createdAt", "endsAt").contains(tokens[0])) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
		}
		Sort.Direction direction;
		try {
			direction = Sort.Direction.fromString(tokens[1]);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
		}
		return PageRequest.of(page, size, Sort.by(direction, tokens[0]));
	}
}
