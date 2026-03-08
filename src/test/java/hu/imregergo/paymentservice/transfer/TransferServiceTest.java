package hu.imregergo.paymentservice.transfer;

import hu.imregergo.paymentservice.account.entity.Account;
import hu.imregergo.paymentservice.account.entity.Currency;
import hu.imregergo.paymentservice.account.exception.AccountNotFoundException;
import hu.imregergo.paymentservice.account.service.AccountService;
import hu.imregergo.paymentservice.eventsystem.service.EventService;
import hu.imregergo.paymentservice.transfer.dto.CreateTransferDto;
import hu.imregergo.paymentservice.transfer.dto.NewTransferEvent;
import hu.imregergo.paymentservice.transfer.entity.Transfer;
import hu.imregergo.paymentservice.transfer.exception.EventSerializationException;
import hu.imregergo.paymentservice.transfer.exception.ExchangeRateApiException;
import hu.imregergo.paymentservice.transfer.exception.NotEnoughBalanceException;
import hu.imregergo.paymentservice.transfer.repository.TransferRepository;
import hu.imregergo.paymentservice.transfer.service.ExchangeRateService;
import hu.imregergo.paymentservice.transfer.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransferServiceTest {

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
    public void setUp() {
        // PlatformTransactionManager mock that executes the lambda immediately
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        lenient().doNothing().when(transactionManager).commit(any());
        lenient().doNothing().when(transactionManager).rollback(any());

        transferService = new TransferService(transferRepository, accountService, exchangeRateService,
                eventService, transactionManager);
        ReflectionTestUtils.setField(transferService, "rateLimitMaxPerMinute", 10);
    }


    // ========== SUCCESSFUL TRANSFER TESTS ==========

    @Test
    void testCreateTransfer_success_sameCurrency() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        // Non-locking fetch (pre-transaction, for early validation)
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        // Locking fetch (inside transaction)
        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        Transfer result = transferService.createTransfer(dto);

        assertThat(result.getId(), is(100L));
        assertThat(result.getAmount(), is(new BigDecimal("100.00")));
        assertThat(result.getRate(), is(BigDecimal.ONE));

        verify(accountService).save(argThat(account ->
                account.getId().equals(1L) && account.getBalance().compareTo(new BigDecimal("400.00")) == 0
        ));
        verify(accountService).save(argThat(account ->
                account.getId().equals(2L) && account.getBalance().compareTo(new BigDecimal("300.00")) == 0
        ));
        verify(eventService, times(1)).newEvent(any(NewTransferEvent.class));
    }

    @Test
    void testCreateTransfer_success_differentCurrencies() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.EUR, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.EUR)).thenReturn(new BigDecimal("0.85"));
        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(101L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        Transfer result = transferService.createTransfer(dto);

        assertThat(result.getRate(), is(new BigDecimal("0.85")));

        verify(accountService).save(argThat(account ->
                account.getId().equals(1L) && account.getBalance().compareTo(new BigDecimal("400.00")) == 0
        ));
        verify(accountService).save(argThat(account ->
                account.getId().equals(2L) && account.getBalance().compareTo(new BigDecimal("285.00")) == 0
        ));
    }

    @Test
    void testCreateTransfer_success_exactBalance() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("500.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("0.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(102L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        transferService.createTransfer(dto);

        verify(accountService).save(argThat(account ->
                account.getId().equals(1L) && account.getBalance().compareTo(BigDecimal.ZERO) == 0
        ));
        verify(accountService).save(argThat(account ->
                account.getId().equals(2L) && account.getBalance().compareTo(new BigDecimal("500.00")) == 0
        ));
    }

    @Test
    void testCreateTransfer_success_verifiesAccountsLockedForUpdate() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("50.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(103L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        transferService.createTransfer(dto);

        // Verify accounts are fetched with lock inside transaction
        verify(accountService, times(1)).getAccount(1L, true);
        verify(accountService, times(1)).getAccount(2L, true);
        // Also verify exchange rate is called with correct currencies
        verify(exchangeRateService, times(1)).getExchangeRate(Currency.USD, Currency.USD);
    }

    @Test
    void testCreateTransfer_success_eventPublishedWithCorrectData() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(104L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        transferService.createTransfer(dto);

        ArgumentCaptor<NewTransferEvent> eventCaptor = ArgumentCaptor.forClass(NewTransferEvent.class);
        verify(eventService).newEvent(eventCaptor.capture());

        NewTransferEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getFromAccountId(), is(1L));
        assertThat(publishedEvent.getToAccountId(), is(2L));
        assertThat(publishedEvent.getAmount(), is(new BigDecimal("100.00")));
    }


    // ========== SELF-TRANSFER TEST ==========

    @Test
    void testCreateTransfer_sameAccount_throwsException() {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(1L);
        dto.setAmount(new BigDecimal("100.00"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("Cannot transfer to the same account"));

        // Verify nothing else was called
        verify(transferRepository, never()).countRecentTransfers(any(), any());
        verify(accountService, never()).getAccount(any());
        verify(exchangeRateService, never()).getExchangeRate(any(), any());
    }


    // ========== NOT ENOUGH BALANCE TESTS ==========

    @Test
    void testCreateTransfer_notEnoughBalance_throwsException() {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("600.00")); // More than available balance

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        // Non-locking fetch for early balance validation
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);

        NotEnoughBalanceException exception = assertThrows(
                NotEnoughBalanceException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("Not enough balance"));

        // Exchange rate must NOT be called (fail fast before network call)
        verify(exchangeRateService, never()).getExchangeRate(any(), any());
        // No accounts locked (no transaction entered)
        verify(accountService, never()).getAccount(eq(1L), anyBoolean());
        verify(accountService, never()).save(any(Account.class));
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(eventService, never()).newEvent(any(NewTransferEvent.class));
    }

    @Test
    void testCreateTransfer_notEnoughBalance_byOneCent() {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("500.01")); // Just 1 cent over

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);

        assertThrows(NotEnoughBalanceException.class, () -> transferService.createTransfer(dto));

        verify(exchangeRateService, never()).getExchangeRate(any(), any());
        verify(accountService, never()).save(any(Account.class));
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(eventService, never()).newEvent(any(NewTransferEvent.class));
    }

    @Test
    void testCreateTransfer_notEnoughBalance_zeroBalance() {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("0.01"));

        Account fromAccount = createAccount(1L, Currency.USD, BigDecimal.ZERO);
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);

        assertThrows(NotEnoughBalanceException.class, () -> transferService.createTransfer(dto));

        verify(exchangeRateService, never()).getExchangeRate(any(), any());
    }


    // ========== ACCOUNT NOT FOUND TESTS ==========

    @Test
    void testCreateTransfer_fromAccountNotFound_throwsException() {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(999L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        when(transferRepository.countRecentTransfers(eq(999L), any(Instant.class))).thenReturn(0);
        // Non-locking fetch: from account (999L) fetched first, throws
        when(accountService.getAccount(999L)).thenThrow(new AccountNotFoundException(999L));

        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("999"));

        // to account was never fetched
        verify(accountService, never()).getAccount(eq(2L));
        verify(accountService, never()).getAccount(eq(2L), anyBoolean());
        verify(exchangeRateService, never()).getExchangeRate(any(), any());
        verify(accountService, never()).save(any(Account.class));
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(eventService, never()).newEvent(any(NewTransferEvent.class));
    }

    @Test
    void testCreateTransfer_toAccountNotFound_throwsException() {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(999L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(999L)).thenThrow(new AccountNotFoundException(999L));

        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("999"));

        verify(accountService, never()).save(any(Account.class));
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(eventService, never()).newEvent(any(NewTransferEvent.class));
    }

    @Test
    void testCreateTransfer_bothAccountsNotFound_throwsExceptionForFromAccount() {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(888L);
        dto.setToAccountId(999L);
        dto.setAmount(new BigDecimal("100.00"));

        when(transferRepository.countRecentTransfers(eq(888L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(888L)).thenThrow(new AccountNotFoundException(888L));

        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("888"));

        // to account was never fetched
        verify(accountService, never()).getAccount(eq(999L));
        verify(accountService, never()).getAccount(eq(999L), anyBoolean());
    }


    // ========== EXCHANGE RATE API FAILURE TESTS ==========

    @Test
    void testCreateTransfer_exchangeRateApiFails_throwsException() {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.EUR, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.EUR))
                .thenThrow(new ExchangeRateApiException("API unavailable"));

        ExchangeRateApiException exception = assertThrows(
                ExchangeRateApiException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("API unavailable"));

        // Exchange rate failed before transaction — no locks acquired
        verify(accountService, never()).getAccount(eq(1L), anyBoolean());
        verify(accountService, never()).save(any(Account.class));
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(eventService, never()).newEvent(any(NewTransferEvent.class));
    }

    @Test
    void testCreateTransfer_exchangeRateApiTimeout_throwsException() {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.GBP, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.GBP))
                .thenThrow(new ExchangeRateApiException("Request timeout"));

        ExchangeRateApiException exception = assertThrows(
                ExchangeRateApiException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("Request timeout"));

        verify(accountService, never()).save(any(Account.class));
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(eventService, never()).newEvent(any(NewTransferEvent.class));
    }


    // ========== EVENT SERIALIZATION EXCEPTION TESTS ==========

    @Test
    void testCreateTransfer_eventServiceThrowsException_wrappedAsEventSerializationException() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(105L);
            return t;
        });
        doThrow(new RuntimeException("Failed to serialize event"))
                .when(eventService).newEvent(any(NewTransferEvent.class));

        EventSerializationException exception = assertThrows(
                EventSerializationException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("serialize"));
    }


    // ========== EDGE CASES ==========

    @Test
    void testCreateTransfer_smallAmount_handlesCorrectly() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("0.01"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("10.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("5.00"));

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(106L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        Transfer result = transferService.createTransfer(dto);

        assertThat(result.getAmount(), is(new BigDecimal("0.01")));
        verify(accountService).save(argThat(account ->
                account.getId().equals(1L) && account.getBalance().compareTo(new BigDecimal("9.99")) == 0
        ));
    }

    @Test
    void testCreateTransfer_largeAmount_handlesCorrectly() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("9999999.9999"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("10000000.00"));
        Account toAccount = createAccount(2L, Currency.USD, BigDecimal.ZERO);

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(107L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        Transfer result = transferService.createTransfer(dto);

        assertThat(result.getAmount(), is(new BigDecimal("9999999.9999")));
        verify(transferRepository, times(1)).save(any(Transfer.class));
    }

    @Test
    void testCreateTransfer_complexExchangeRate_calculatesCorrectly() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.EUR, new BigDecimal("200.00"));

        BigDecimal exchangeRate = new BigDecimal("0.8567");

        when(transferRepository.countRecentTransfers(eq(1L), any(Instant.class))).thenReturn(0);
        when(accountService.getAccount(1L)).thenReturn(fromAccount);
        when(accountService.getAccount(2L)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.EUR)).thenReturn(exchangeRate);
        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(108L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        Transfer result = transferService.createTransfer(dto);

        assertThat(result.getRate(), is(exchangeRate));
        verify(accountService).save(argThat(account ->
                account.getId().equals(2L) && account.getBalance().compareTo(new BigDecimal("285.67")) == 0
        ));
    }


    // ========== HELPER METHODS ==========

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
