package hu.imregergo.paymentservice.transfer.service;

import hu.imregergo.paymentservice.account.entity.Account;
import hu.imregergo.paymentservice.account.exception.AccountNotFoundException;
import hu.imregergo.paymentservice.account.service.AccountService;
import hu.imregergo.paymentservice.eventsystem.service.EventService;
import hu.imregergo.paymentservice.transfer.dto.CreateTransferDto;
import hu.imregergo.paymentservice.transfer.dto.NewTransferEvent;
import hu.imregergo.paymentservice.transfer.entity.Transfer;
import hu.imregergo.paymentservice.transfer.exception.EventSerializationException;
import hu.imregergo.paymentservice.transfer.exception.ExchangeRateApiException;
import hu.imregergo.paymentservice.transfer.exception.NotEnoughBalanceException;
import hu.imregergo.paymentservice.transfer.exception.RateLimitExceededException;
import hu.imregergo.paymentservice.transfer.mapper.TransferMapper;
import hu.imregergo.paymentservice.transfer.repository.TransferRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;
    private final EventService eventService;
    private final TransactionTemplate transactionTemplate;

    @Value("${transfer.rate-limit.max-per-minute:10}")
    private int rateLimitMaxPerMinute;

    public TransferService(TransferRepository transferRepository,
                           AccountService accountService,
                           ExchangeRateService exchangeRateService,
                           EventService eventService,
                           PlatformTransactionManager transactionManager) {
        this.transferRepository = transferRepository;
        this.accountService = accountService;
        this.exchangeRateService = exchangeRateService;
        this.eventService = eventService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Creates a transfer between two accounts.
     *
     * Flow (outside transaction):
     *   1. Self-transfer check
     *   2. Rate limit check
     *   3. Fetch accounts without lock (for early validation)
     *   4. Early balance check (fail fast before any network call)
     *   5. Call exchange rate service (HTTP call — outside DB transaction)
     *
     * Then inside transaction:
     *   6. Lock accounts in consistent order (deadlock prevention)
     *   7. Re-validate balance (TOCTOU protection)
     *   8. Execute transfer, save, publish event
     */
    public Transfer createTransfer(CreateTransferDto dto) {
        // Step 1: Self-transfer prevention
        if (dto.getFromAccountId().equals(dto.getToAccountId())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        // Step 2: Rate limit check
        Instant oneMinuteAgo = Instant.now().minus(1, ChronoUnit.MINUTES);
        int recentCount = transferRepository.countRecentTransfers(dto.getFromAccountId(), oneMinuteAgo);
        if (recentCount >= rateLimitMaxPerMinute) {
            throw new RateLimitExceededException();
        }

        // Step 3: Fetch accounts without lock for early validation
        Account fromAccount = accountService.getAccount(dto.getFromAccountId());
        Account toAccount = accountService.getAccount(dto.getToAccountId());

        // Step 4: Early balance check (fail fast before network call)
        if (fromAccount.getBalance().compareTo(dto.getAmount()) < 0) {
            throw new NotEnoughBalanceException();
        }

        // Step 5: Fetch exchange rate OUTSIDE transaction (avoids holding DB connection during HTTP call)
        BigDecimal exchangeRate = exchangeRateService.getExchangeRate(
                fromAccount.getCurrency(), toAccount.getCurrency());

        // Steps 6-8: Execute inside a transaction
        return transactionTemplate.execute(status -> {
            // Lock accounts in consistent order to prevent deadlocks
            Account lockedFrom;
            Account lockedTo;
            if (dto.getToAccountId() > dto.getFromAccountId()) {
                lockedFrom = accountService.getAccount(dto.getFromAccountId(), true);
                lockedTo = accountService.getAccount(dto.getToAccountId(), true);
            } else {
                lockedTo = accountService.getAccount(dto.getToAccountId(), true);
                lockedFrom = accountService.getAccount(dto.getFromAccountId(), true);
            }

            // TOCTOU re-validation: balance may have changed since early check
            if (lockedFrom.getBalance().compareTo(dto.getAmount()) < 0) {
                throw new NotEnoughBalanceException();
            }

            Transfer transfer = new Transfer();
            transfer.setFromAccount(AggregateReference.to(lockedFrom.getId()));
            transfer.setToAccount(AggregateReference.to(lockedTo.getId()));
            transfer.setAmount(dto.getAmount());
            transfer.setRate(exchangeRate);

            lockedFrom.setBalance(lockedFrom.getBalance().subtract(dto.getAmount()));
            lockedTo.setBalance(lockedTo.getBalance().add(dto.getAmount().multiply(exchangeRate)));

            accountService.save(lockedFrom);
            accountService.save(lockedTo);

            Transfer saved = transferRepository.save(transfer);

            try {
                eventService.newEvent(TransferMapper.toNewTransferEvent(saved));
            } catch (Exception e) {
                throw new EventSerializationException(e);
            }

            return saved;
        });
    }
}
