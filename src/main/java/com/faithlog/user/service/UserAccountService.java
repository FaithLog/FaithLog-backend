package com.faithlog.user.service;

import com.faithlog.user.service.command.DeleteMyAccountCommand;
import com.faithlog.user.service.result.DeleteMyAccountResult;
import org.springframework.stereotype.Service;

@Service
public class UserAccountService {

	private final AccountWithdrawalCommandService accountWithdrawalCommandService;

	public UserAccountService(AccountWithdrawalCommandService accountWithdrawalCommandService) {
		this.accountWithdrawalCommandService = accountWithdrawalCommandService;
	}

	public DeleteMyAccountResult deleteMyAccount(DeleteMyAccountCommand command) {
		return accountWithdrawalCommandService.deleteMyAccount(command);
	}
}
