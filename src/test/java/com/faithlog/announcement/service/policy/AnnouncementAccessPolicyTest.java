package com.faithlog.announcement.service.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnnouncementAccessPolicyTest {

	@Mock CampusAccessPolicy campusAccessPolicy;
	@Mock CampusMemberRepositoryPort campusMembers;

	@Test
	void service_admin_without_active_campus_membership_cannot_use_public_announcement_read() {
		when(campusAccessPolicy.getActiveUser(99L))
			.thenReturn(new CampusUserLookupResult(99L, "admin", "admin@example.com", "ADMIN", true));
		when(campusMembers.findByCampusIdAndUserId(7L, 99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> new AnnouncementAccessPolicy(campusAccessPolicy, campusMembers)
			.requireActiveMember(7L, 99L))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_ACCESS_FORBIDDEN));
	}
}
