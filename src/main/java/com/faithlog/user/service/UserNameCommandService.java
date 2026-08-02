package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.UpdateMyNameCommand;
import com.faithlog.user.service.result.UserMeResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserNameCommandService {

	private final UserRepository userRepository;
	private final CampusMembershipQuerySupport campusMembershipQuerySupport;

	public UserNameCommandService(
		UserRepository userRepository,
		CampusMembershipQuerySupport campusMembershipQuerySupport
	) {
		this.userRepository = userRepository;
		this.campusMembershipQuerySupport = campusMembershipQuerySupport;
	}

	@Transactional
	public UserMeResult updateMyName(UpdateMyNameCommand command) {
		User user = userRepository.findByIdForUpdate(command.userId())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}

		user.changeName(command.name());
		return campusMembershipQuerySupport.toUserMeResult(user);
	}
}
