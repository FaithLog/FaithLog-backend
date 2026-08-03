package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.user.service.policy.YearlyRecapPeriod;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class YearlyRecapTransactionContractTest {

	@Test
	void first_snapshot_get_and_presented_outer_boundary_use_repeatable_read() throws Exception {
		assertRepeatableRead(YearlyRecapSnapshotService.class.getDeclaredMethod(
			"getOrCreate", Long.class, YearlyRecapPeriod.class
		));
		assertRepeatableRead(YearlyRecapPresentationCommandService.class.getDeclaredMethod(
			"markPresented", Long.class, int.class
		));
	}

	private void assertRepeatableRead(Method method) {
		Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

		assertThat(transactional).isNotNull();
		assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
	}
}
