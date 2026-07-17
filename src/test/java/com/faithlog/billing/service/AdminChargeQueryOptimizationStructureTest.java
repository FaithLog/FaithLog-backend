package com.faithlog.billing.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminChargeQueryOptimizationStructureTest {

	private static final Path ADMIN_CHARGE_QUERY_SERVICE = Path.of(
		"src/main/java/com/faithlog/billing/service/AdminChargeQueryService.java"
	);

	@Test
	void campusAggregateEndpointsDoNotUsePerMemberUserLookup() {
		String source = read(ADMIN_CHARGE_QUERY_SERVICE);
		String allAccounts = methodBody(source, "listAdminCampusCharges");
		String myAccounts = methodBody(source, "listAdminCampusChargesForMyAccounts");

		assertAll(
			() -> assertFalse(
				allAccounts.contains("targetUsers("),
				"관리자 캠퍼스 집계는 ACTIVE 멤버마다 User를 조회하는 targetUsers 경로를 사용하면 안 됩니다."
			),
			() -> assertFalse(
				myAccounts.contains("targetUsers("),
				"내 계좌 캠퍼스 집계도 ACTIVE 멤버마다 User를 조회하는 targetUsers 경로를 사용하면 안 됩니다."
			)
		);
	}

	@Test
	void campusAggregateEndpointsDelegateSummarySortingAndPagingToDatabase() {
		String source = read(ADMIN_CHARGE_QUERY_SERVICE);
		String allAccounts = methodBody(source, "listAdminCampusCharges");
		String myAccounts = methodBody(source, "listAdminCampusChargesForMyAccounts");

		assertAll(
			() -> assertDatabaseAggregatePath(allAccounts, "관리자 캠퍼스 집계"),
			() -> assertDatabaseAggregatePath(myAccounts, "내 계좌 캠퍼스 집계"),
			() -> assertTrue(
				source.contains("AdminChargeAggregationQueryPort"),
				"청구 Entity 전체 로딩 대신 summary/member page/count projection을 반환하는 전용 read port가 필요합니다."
			),
			() -> assertTrue(
				source.contains("adminChargeAggregationQueryPort.summarize("),
				"전체 금액 summary는 DB 조건 집계 read port에서 계산해야 합니다."
			),
			() -> assertTrue(
				source.contains("adminChargeAggregationQueryPort.findMemberPage("),
				"회원별 합계, 정렬, page, count는 DB projection read port에서 계산해야 합니다."
			)
		);
	}

	private void assertDatabaseAggregatePath(String methodBody, String label) {
		assertAll(
			() -> assertFalse(
				methodBody.contains("chargeItemRepository.searchCharges("),
				label + "는 조건에 맞는 ChargeItem 전체를 JVM으로 materialize하면 안 됩니다."
			),
			() -> assertFalse(
				methodBody.contains("aggregateMembers("),
				label + "는 회원별 합계/정렬/page를 JVM에서 처리하면 안 됩니다."
			),
			() -> assertFalse(
				methodBody.contains("summarize(charges)"),
				label + "는 전체 상태별 금액을 JVM에서 합산하면 안 됩니다."
			)
		);
	}

	private String methodBody(String source, String methodName) {
		int methodStart = source.indexOf(methodName + "(");
		assertTrue(methodStart >= 0, methodName + " 메서드를 찾을 수 없습니다.");
		int bodyStart = source.indexOf('{', methodStart);
		assertTrue(bodyStart >= 0, methodName + " 본문 시작을 찾을 수 없습니다.");
		int depth = 0;
		for (int index = bodyStart; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(bodyStart, index + 1);
				}
			}
		}
		throw new IllegalStateException(methodName + " 본문 끝을 찾을 수 없습니다.");
	}

	private String read(Path source) {
		try {
			return Files.readString(source);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
