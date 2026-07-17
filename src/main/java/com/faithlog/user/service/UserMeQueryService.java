package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.result.UserMeResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserMeQueryService {

	private final UserRepository userRepository;
	private final CampusMembershipQuerySupport campusMembershipQuerySupport;

	public UserMeQueryService(
		UserRepository userRepository,
		CampusMembershipQuerySupport campusMembershipQuerySupport
	) {
		this.userRepository = userRepository;
		this.campusMembershipQuerySupport = campusMembershipQuerySupport;
	}

	@Transactional(readOnly = true)
	public UserMeResult getCurrentUser(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return campusMembershipQuerySupport.toUserMeResult(user);
	}
}
