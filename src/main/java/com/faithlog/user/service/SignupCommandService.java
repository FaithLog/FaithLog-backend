package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.SignupCommand;
import com.faithlog.user.service.result.SignupResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupCommandService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public SignupCommandService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public SignupResult signup(SignupCommand command) {
		if (userRepository.existsByEmail(command.email())) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}

		User user = User.create(command.name(), command.email(), passwordEncoder.encode(command.password()));
		User savedUser = userRepository.save(user);
		return SignupResult.from(savedUser);
	}
}
