package hu.imregergo.paymentservice.account.service;

import hu.imregergo.paymentservice.account.dto.CreateAccountDto;
import hu.imregergo.paymentservice.account.entity.Account;
import hu.imregergo.paymentservice.account.exception.AccountNotFoundException;
import hu.imregergo.paymentservice.account.repository.AccountRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    /**
     * Creates a new account with the provided details.
     *
     * @param dto The data transfer object containing the details for the new account, such as currency and initial balance.
     * @return The created Account entity with its generated ID and initial balance set.
     */
    public Account createAccount(@Valid CreateAccountDto dto) {
        Account account = new Account();
        account.setCurrency(dto.getCurrency());
        account.setInitialBalance(dto.getInitialBalance());
        account.setBalance(dto.getInitialBalance());

        account = accountRepository.save(account);
        return account;
    }


    /**
     * Retrieves a list of all accounts in the system.
     * @return A list of Account entities representing all accounts currently stored in the database.
     */
    public List<Account> getAccounts() {
        return accountRepository.findAll();
    }


    /**
     * Retrieves an account by its ID.
     * @param fromAccountId The ID of the account to retrieve.
     * @return The Account entity corresponding to the provided ID.
     * @throws AccountNotFoundException If no account with the given ID exists in the database.
     */
    public Account getAccount(Long fromAccountId) throws AccountNotFoundException {
        return getAccount(fromAccountId, false);
    }

    /**
     * Retrieves an account by its ID, with an option to lock the account for update.
     * @param fromAccountId The ID of the account to retrieve.
     * @param lock If true, the account will be locked for update to prevent concurrent modifications.
     * @return The Account entity corresponding to the provided ID.
     * @throws AccountNotFoundException If no account with the given ID exists in the database.
     */
    public Account getAccount(Long fromAccountId, boolean lock) throws AccountNotFoundException {
        if(lock) {
            return accountRepository.findByIdAndLock(fromAccountId).orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        }
        return accountRepository.findById(fromAccountId).orElseThrow(() -> new AccountNotFoundException(fromAccountId));
    }

    /**
     * Saves the provided account entity to the database, updating its last modified timestamp.
     * @param acc The Account entity to save, which may have updated fields such as balance or currency.
     * @return The saved Account entity with any changes persisted to the database.
     */
    public Account save(Account acc) {
        acc.setUpdatedAt(Instant.now());
        return accountRepository.save(acc);
    }
}
