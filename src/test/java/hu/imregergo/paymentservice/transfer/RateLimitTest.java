package hu.imregergo.paymentservice.transfer;

import hu.imregergo.paymentservice.account.entity.Account;
import hu.imregergo.paymentservice.account.entity.Currency;
import hu.imregergo.paymentservice.account.service.AccountService;
import hu.imregergo.paymentservice.eventsystem.service.EventService;
import hu.imregergo.paymentservice.transfer.dto.CreateTransferDto;
import hu.imregergo.paymentservice.transfer.dto.NewTransferEvent;
import hu.imregergo.paymentservice.transfer.entity.Transfer;
import hu.imregergo.paymentservice.transfer.exception.RateLimitExceededException;
import hu.imregergo.paymentservice.transfer.repository.TransferRepository;
import hu.imregergo.paymentservice.transfer.service.ExchangeRateService;
import hu.imregergo.paymentservice.transfer.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RateLimitTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private EventService eventService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        lenient().doNothing().when(transactionManager).commit(any());
        lenient().doNothing().when(transactionManager).rollback(any());

        transferService = new TransferService(transferRepository, accountService, exchangeRateService,
                eventService, transactionManager);
        ReflectionTestUtils.setField(transferService, "rateLimitMaxPerMinute", 10);
    }

    @Test
    void testCreateTransfer_underRateLimit_succeeds() throws Exception {
        // 9 recent transfers — should pass the rate limit check
        CreateTransferDto dto = buildDto(1L, 2L, new BigDecimal("100.00"));
        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(9);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(accountService.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> {
            Transfer t = i.getArgument(0);
            t.setId(1L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        // Should not throw
        Transfer result = transferService.createTransfer(dto);
        assertNotNull(result);
    }

    @Test
    void testCreateTransfer_atRateLimit_throws() {
        // Exactly 10 recent transfers — should be rejected
        CreateTransferDto dto = buildDto(1L, 2L, new BigDecimal("100.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(10);

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("rate limit exceeded"));
        // No account fetching, no exchange rate call
        verify(accountService, never()).getAccount(any());
        verify(exchangeRateService, never()).getExchangeRate(any(), any());
        verify(transferRepository, never()).save(any(Transfer.class));
    }

    @Test
    void testCreateTransfer_overRateLimit_throws() {
        // 15 recent transfers — well over the limit
        CreateTransferDto dto = buildDto(1L, 2L, new BigDecimal("50.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(15);

        assertThrows(RateLimitExceededException.class, () -> transferService.createTransfer(dto));

        verify(accountService, never()).getAccount(any());
        verify(exchangeRateService, never()).getExchangeRate(any(), any());
    }

    @Test
    void testCreateTransfer_rateLimitChecksCorrectAccount() {
        // Rate limit should use fromAccountId, not toAccountId
        CreateTransferDto dto = buildDto(5L, 10L, new BigDecimal("100.00"));

        when(transferRepository.countRecentTransfers(eq(5L), any(Instant.class))).thenReturn(10);

        assertThrows(RateLimitExceededException.class, () -> transferService.createTransfer(dto));

        verify(transferRepository).countRecentTransfers(eq(5L), any(Instant.class));
        verify(transferRepository, never()).countRecentTransfers(eq(10L), any(Instant.class));
    }

    // ========== HELPERS ==========

    private CreateTransferDto buildDto(Long fromId, Long toId, BigDecimal amount) {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(fromId);
        dto.setToAccountId(toId);
        dto.setAmount(amount);
        return dto;
    }

    private Account createAccount(Long id, Currency currency, BigDecimal balance) {
        Account account = new Account();
        account.setId(id);
        account.setCurrency(currency);
        account.setBalance(balance);
        account.setInitialBalance(balance);
        account.setCreatedAt(Instant.now());
        return account;
    }
}
