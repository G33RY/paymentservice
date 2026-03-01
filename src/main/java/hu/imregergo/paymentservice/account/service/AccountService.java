package hu.imregergo.paymentservice.account.service;

import hu.imregergo.paymentservice.account.dto.CreateAccountDto;
import hu.imregergo.paymentservice.account.entity.Account;
import hu.imregergo.paymentservice.account.exception.AccountNotFoundException;
import hu.imregergo.paymentservice.account.repository.AccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public Account createAccount(@Valid CreateAccountDto dto) {
        Account account = new Account();
        account.setCurrency(dto.getCurrency());
        account.setInitialBalance(dto.getInitialBalance());
        account.setBalance(dto.getInitialBalance());

        account = accountRepository.save(account);
        return account;
    }


    public List<Account> getAccounts() {
        return accountRepository.findAll();
    }


    public Account getAccount(Long fromAccountId) throws AccountNotFoundException {
        return getAccount(fromAccountId, false);
    }
    public Account getAccount(Long fromAccountId, boolean lock) throws AccountNotFoundException {
        if(lock) {
            return accountRepository.findByIdAndLock(fromAccountId).orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        }
        return accountRepository.findById(fromAccountId).orElseThrow(() -> new AccountNotFoundException(fromAccountId));
    }

    public Account save(Account acc) {
        acc.setUpdatedAt(Instant.now());
        return accountRepository.save(acc);
    }
}
