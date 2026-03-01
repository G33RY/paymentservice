package hu.imregergo.paymentservice.account.mapper;

import hu.imregergo.paymentservice.account.dto.AccountResponse;
import hu.imregergo.paymentservice.account.entity.Account;

public final class AccountMapper {

    public static AccountResponse toResponse(Account account) {
        if (account == null) {
            return null;
        }
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setBalance(account.getBalance());
        response.setCurrency(account.getCurrency());
        return response;
    }
}
