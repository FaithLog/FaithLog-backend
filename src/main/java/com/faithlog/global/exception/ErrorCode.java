package com.faithlog.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	GLOBAL_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	GLOBAL_INVALID_JSON(HttpStatus.BAD_REQUEST, "잘못된 JSON 요청입니다."),

	AUTH_EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다."),
	AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

	ADMIN_ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "서비스 관리자 권한이 없습니다."),
	ADMIN_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
	ADMIN_LAST_ADMIN_DEMOTION_FORBIDDEN(HttpStatus.CONFLICT, "마지막 서비스 관리자는 강등할 수 없습니다."),
	ADMIN_INVALID_PAGE(HttpStatus.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다."),
	ADMIN_INVALID_SIZE(HttpStatus.BAD_REQUEST, "페이지 크기는 1 이상 100 이하이어야 합니다."),
	ADMIN_INVALID_SORT_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 형식입니다."),
	ADMIN_INVALID_SORT_PROPERTY(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 기준입니다."),
	ADMIN_INVALID_SORT_DIRECTION(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 방향입니다."),

	CAMPUS_CREATE_FORBIDDEN(HttpStatus.FORBIDDEN, "캠퍼스 생성 권한이 없습니다."),
	CAMPUS_INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "유효하지 않은 초대코드입니다."),
	CAMPUS_ALREADY_JOINED(HttpStatus.BAD_REQUEST, "이미 가입된 캠퍼스입니다."),
	CAMPUS_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "캠퍼스 멤버를 찾을 수 없습니다."),
	CAMPUS_MEMBER_MANAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "캠퍼스 멤버 관리 권한이 없습니다."),
	CAMPUS_ROLE_CHANGE_FORBIDDEN(HttpStatus.FORBIDDEN, "캠퍼스 역할 변경 권한이 없습니다."),
	CAMPUS_ROLE_HIERARCHY_FORBIDDEN(HttpStatus.FORBIDDEN, "상위 캠퍼스 역할은 변경할 수 없습니다."),
	CAMPUS_DUTY_ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "커피 담당자 배정을 찾을 수 없습니다."),
	CAMPUS_VIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "캠퍼스 조회 권한이 없습니다."),
	CAMPUS_NOT_FOUND(HttpStatus.NOT_FOUND, "캠퍼스를 찾을 수 없습니다."),
	CAMPUS_INVITE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "초대코드 생성에 실패했습니다."),

	DEVOTION_INVALID_WEEK_START_DATE(HttpStatus.BAD_REQUEST, "weekStartDate는 월요일이어야 합니다."),
	DEVOTION_INVALID_YEAR_MONTH(HttpStatus.BAD_REQUEST, "조회 연월이 올바르지 않습니다."),
	DEVOTION_DAILY_CHECK_DATE_OUT_OF_WEEK(HttpStatus.BAD_REQUEST, "dailyChecks[].recordDate는 요청 주차 안의 날짜여야 합니다."),
	DEVOTION_INVALID_SATURDAY_LATE_MINUTES(HttpStatus.BAD_REQUEST, "saturdayLateMinutes는 0 이상이어야 합니다."),
	DEVOTION_WEEKLY_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출된 주간 경건생활은 수정할 수 없습니다."),
	DEVOTION_WEEKLY_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "주간 경건생활 기록을 찾을 수 없습니다."),
	DEVOTION_ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "경건생활 접근 권한이 없습니다."),
	DEVOTION_ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "경건생활 관리자 권한이 없습니다."),
	DEVOTION_PENALTY_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "벌금 규칙을 찾을 수 없습니다."),
	DEVOTION_PENALTY_RULE_ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "벌금 규칙 조회 권한이 없습니다."),
	DEVOTION_PENALTY_RULE_MANAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "벌금 규칙 관리 권한이 없습니다."),
	DEVOTION_PENALTY_RULE_INVALID_TYPE_PAIR(HttpStatus.BAD_REQUEST, "벌금 규칙 타입과 계산 타입 조합이 올바르지 않습니다."),
	DEVOTION_PENALTY_RULE_INVALID_VALUE(HttpStatus.BAD_REQUEST, "벌금 규칙 기준값과 금액은 0 이상이어야 합니다."),

	BILLING_INVALID_PAGE(HttpStatus.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다."),
	BILLING_INVALID_SIZE(HttpStatus.BAD_REQUEST, "페이지 크기는 1 이상 100 이하이어야 합니다."),
	BILLING_INVALID_SORT_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 형식입니다."),
	BILLING_INVALID_SORT_PROPERTY(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 기준입니다."),
	BILLING_INVALID_SORT_DIRECTION(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 방향입니다."),
	BILLING_INVALID_YEAR_MONTH(HttpStatus.BAD_REQUEST, "조회 연월이 올바르지 않습니다."),
	BILLING_PAYMENT_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "납부 계좌를 찾을 수 없습니다."),
	BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "납부 계좌 관리 권한이 없습니다."),
	BILLING_PAYMENT_ACCOUNT_LIST_FORBIDDEN(HttpStatus.FORBIDDEN, "캠퍼스 납부 계좌 조회 권한이 없습니다."),
	BILLING_CHARGE_LIST_FORBIDDEN(HttpStatus.FORBIDDEN, "청구 조회 권한이 없습니다."),
	BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING(HttpStatus.BAD_REQUEST, "관리자에게 문의하세요"),
	BILLING_TERMINAL_CHARGE_UPDATE_FORBIDDEN(HttpStatus.BAD_REQUEST, "이미 종료된 청구는 갱신할 수 없습니다."),
	BILLING_CHARGE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "청구 항목을 찾을 수 없습니다."),
	BILLING_MY_CHARGE_PAYMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 청구 항목만 납부 완료 처리할 수 있습니다."),
	BILLING_MY_CHARGE_CAMPUS_MISMATCH(HttpStatus.FORBIDDEN, "청구 항목의 캠퍼스가 요청 캠퍼스와 일치하지 않습니다."),
	BILLING_MY_CHARGE_PAYMENT_CONFLICT(HttpStatus.CONFLICT, "미납 상태의 청구만 납부 완료 처리할 수 있습니다."),
	BILLING_ADMIN_PAID_FORBIDDEN(HttpStatus.BAD_REQUEST, "관리자는 청구를 PAID로 변경할 수 없습니다."),
	BILLING_CHARGE_STATUS_TRANSITION_CONFLICT(HttpStatus.CONFLICT, "허용되지 않는 청구 상태 전이입니다."),
	BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "청구 상태 변경 권한이 없습니다."),

	POLL_TEMPLATE_MANAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "투표 템플릿 관리 권한이 없습니다."),
	POLL_CREATE_FORBIDDEN(HttpStatus.FORBIDDEN, "투표 생성 권한이 없습니다."),
	POLL_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "투표 템플릿을 찾을 수 없습니다."),
	POLL_TEMPLATE_INACTIVE(HttpStatus.BAD_REQUEST, "비활성화된 투표 템플릿은 사용할 수 없습니다."),
	POLL_MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "커피 메뉴를 찾을 수 없습니다."),
	POLL_MENU_INACTIVE(HttpStatus.BAD_REQUEST, "비활성화된 커피 메뉴는 사용할 수 없습니다."),
	POLL_COFFEE_BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "커피 브랜드를 찾을 수 없습니다."),
	POLL_COFFEE_DUTY_MISSING(HttpStatus.BAD_REQUEST, "관리자에게 문의하세요"),
	POLL_INVALID_OPTION(HttpStatus.BAD_REQUEST, "투표 선택지가 올바르지 않습니다."),
	POLL_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "투표 기간이 올바르지 않습니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}
}
