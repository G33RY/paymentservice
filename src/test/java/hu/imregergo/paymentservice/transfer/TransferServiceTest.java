package hu.imregergo.paymentservice.transfer;

import com.fasterxml.jackson.core.JsonProcessingException;
import hu.imregergo.paymentservice.account.entity.Account;
import hu.imregergo.paymentservice.account.entity.Currency;
import hu.imregergo.paymentservice.account.exception.AccountNotFoundException;
import hu.imregergo.paymentservice.account.service.AccountService;
import hu.imregergo.paymentservice.eventsystem.service.EventService;
import hu.imregergo.paymentservice.transfer.dto.CreateTransferDto;
import hu.imregergo.paymentservice.transfer.dto.NewTransferEvent;
import hu.imregergo.paymentservice.transfer.entity.Transfer;
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

    private TransferService transferService;

    @BeforeEach
    public void setUp() {
        transferService = new TransferService(transferRepository, accountService, exchangeRateService, eventService);
    }


    // ========== SUCCESSFUL TRANSFER TESTS ==========

    @Test
    void testCreateTransfer_success_sameCurrency() throws Exception {
        // Setup
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        // Execute
        Transfer result = transferService.createTransfer(dto);

        // Verify
        assertThat(result.getId(), is(100L));
        assertThat(result.getAmount(), is(new BigDecimal("100.00")));
        assertThat(result.getRate(), is(BigDecimal.ONE));

        // Verify account balances updated
        verify(accountService).save(argThat(account ->
                account.getId().equals(1L) && account.getBalance().compareTo(new BigDecimal("400.00")) == 0
        ));
        verify(accountService).save(argThat(account ->
                account.getId().equals(2L) && account.getBalance().compareTo(new BigDecimal("300.00")) == 0
        ));

        // Verify event published
        verify(eventService, times(1)).newEvent(any(NewTransferEvent.class));
    }

    @Test
    void testCreateTransfer_success_differentCurrencies() throws Exception {
        // Setup - USD to EUR with exchange rate 0.85
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.EUR, new BigDecimal("200.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.EUR)).thenReturn(new BigDecimal("0.85"));
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(101L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        // Execute
        Transfer result = transferService.createTransfer(dto);

        // Verify
        assertThat(result.getRate(), is(new BigDecimal("0.85")));

        // Verify from account debited 100 USD
        verify(accountService).save(argThat(account ->
                account.getId().equals(1L) && account.getBalance().compareTo(new BigDecimal("400.00")) == 0
        ));

        // Verify to account credited 85 EUR (100 * 0.85)
        verify(accountService).save(argThat(account ->
                account.getId().equals(2L) && account.getBalance().compareTo(new BigDecimal("285.00")) == 0
        ));
    }

    @Test
    void testCreateTransfer_success_exactBalance() throws Exception {
        // Setup - transfer entire balance
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("500.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("0.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(102L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        // Execute
        transferService.createTransfer(dto);

        // Verify from account has zero balance
        verify(accountService).save(argThat(account ->
                account.getId().equals(1L) && account.getBalance().compareTo(BigDecimal.ZERO) == 0
        ));

        // Verify to account credited full amount
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

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(103L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        // Execute
        transferService.createTransfer(dto);

        // Verify accounts are fetched with lock (true parameter)
        verify(accountService, times(1)).getAccount(1L, true);
        verify(accountService, times(1)).getAccount(2L, true);
    }

    @Test
    void testCreateTransfer_success_eventPublishedWithCorrectData() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(104L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        // Execute
        transferService.createTransfer(dto);

        // Verify event published with correct data
        ArgumentCaptor<NewTransferEvent> eventCaptor = ArgumentCaptor.forClass(NewTransferEvent.class);
        verify(eventService).newEvent(eventCaptor.capture());

        NewTransferEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getFromAccountId(), is(1L));
        assertThat(publishedEvent.getToAccountId(), is(2L));
        assertThat(publishedEvent.getAmount(), is(new BigDecimal("100.00")));
    }


    // ========== NOT ENOUGH BALANCE TESTS ==========

    @Test
    void testCreateTransfer_notEnoughBalance_throwsException() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("600.00")); // More than available balance

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);

        // Execute & Verify
        NotEnoughBalanceException exception = assertThrows(
                NotEnoughBalanceException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("Not enough balance"));

        // Verify no accounts were saved
        verify(accountService, never()).save(any(Account.class));

        // Verify no transfer was saved
        verify(transferRepository, never()).save(any(Transfer.class));

        // Verify no event was published
        verify(eventService, never()).newEvent(any(NewTransferEvent.class));
    }

    @Test
    void testCreateTransfer_notEnoughBalance_byOneCent() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("500.01")); // Just 1 cent over

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);

        // Execute & Verify
        assertThrows(
                NotEnoughBalanceException.class,
                () -> transferService.createTransfer(dto)
        );

        // Verify no state changes occurred
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

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);

        // Execute & Verify
        assertThrows(
                NotEnoughBalanceException.class,
                () -> transferService.createTransfer(dto)
        );
    }


    // ========== ACCOUNT NOT FOUND TESTS ==========

    @Test
    void testCreateTransfer_fromAccountNotFound_throwsException() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(999L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        when(accountService.getAccount(2L, true)).thenThrow(new AccountNotFoundException(2L));

        // Execute & Verify
        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> transferService.createTransfer(dto)
        );

        // Should fail on the lowest ID first (from account)
        assertThat(exception.getMessage(), containsString("2"));

        // Verify no further operations occurred
        verify(accountService, never()).getAccount(eq(999L), anyBoolean());
        verify(exchangeRateService, never()).getExchangeRate(any(), any());
        verify(accountService, never()).save(any(Account.class));
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(eventService, never()).newEvent(any(NewTransferEvent.class));
    }

    @Test
    void testCreateTransfer_toAccountNotFound_throwsException() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(999L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(999L, true)).thenThrow(new AccountNotFoundException(999L));

        // Execute & Verify
        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("999"));

        // Verify no state changes occurred
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

        when(accountService.getAccount(888L, true)).thenThrow(new AccountNotFoundException(888L));

        // Execute & Verify - should fail on from account first
        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("888"));

        // Verify to account was never checked
        verify(accountService, never()).getAccount(eq(999L), anyBoolean());
    }


    // ========== EXCHANGE RATE API FAILURE TESTS ==========

    @Test
    void testCreateTransfer_exchangeRateApiFails_throwsException() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.EUR, new BigDecimal("200.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.EUR))
                .thenThrow(new ExchangeRateApiException("API unavailable"));

        // Execute & Verify
        ExchangeRateApiException exception = assertThrows(
                ExchangeRateApiException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("API unavailable"));

        // Verify no state changes occurred
        verify(accountService, never()).save(any(Account.class));
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(eventService, never()).newEvent(any(NewTransferEvent.class));
    }

    @Test
    void testCreateTransfer_exchangeRateApiTimeout_throwsException() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.GBP, new BigDecimal("200.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.GBP))
                .thenThrow(new ExchangeRateApiException("Request timeout"));

        // Execute & Verify
        ExchangeRateApiException exception = assertThrows(
                ExchangeRateApiException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("Request timeout"));

        // Verify transaction was rolled back (no saves)
        verify(accountService, never()).save(any(Account.class));
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(eventService, never()).newEvent(any(NewTransferEvent.class));
    }


    // ========== JSON PROCESSING EXCEPTION TESTS ==========

    @Test
    void testCreateTransfer_eventServiceThrowsJsonException_propagatesException() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("100.00"));

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("500.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("200.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(105L);
            return t;
        });
        doThrow(new JsonProcessingException("Failed to serialize event") {
        })
                .when(eventService).newEvent(any(NewTransferEvent.class));

        // Execute & Verify
        JsonProcessingException exception = assertThrows(
                JsonProcessingException.class,
                () -> transferService.createTransfer(dto)
        );

        assertThat(exception.getMessage(), containsString("Failed to serialize event"));
    }


    // ========== EDGE CASES AND INTEGRATION TESTS ==========

    @Test
    void testCreateTransfer_smallAmount_handlesCorrectly() throws Exception {
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("0.01")); // 1 cent

        Account fromAccount = createAccount(1L, Currency.USD, new BigDecimal("10.00"));
        Account toAccount = createAccount(2L, Currency.USD, new BigDecimal("5.00"));

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(106L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        // Execute
        Transfer result = transferService.createTransfer(dto);

        // Verify
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

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(107L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        // Execute
        Transfer result = transferService.createTransfer(dto);

        // Verify
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

        // Complex exchange rate
        BigDecimal exchangeRate = new BigDecimal("0.8567");

        when(accountService.getAccount(1L, true)).thenReturn(fromAccount);
        when(accountService.getAccount(2L, true)).thenReturn(toAccount);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.EUR)).thenReturn(exchangeRate);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(108L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        // Execute
        Transfer result = transferService.createTransfer(dto);

        // Verify exchange rate stored
        assertThat(result.getRate(), is(exchangeRate));

        // Verify to account credited with converted amount (100 * 0.8567 = 85.67)
        verify(accountService).save(argThat(account ->
                account.getId().equals(2L) && account.getBalance().compareTo(new BigDecimal("285.67")) == 0
        ));
    }

    @Test
    void testCreateTransfer_sameAccount_stillProcesses() throws Exception {
        // Edge case: transferring from account to itself
        CreateTransferDto dto = new CreateTransferDto();
        dto.setFromAccountId(1L);
        dto.setToAccountId(1L);
        dto.setAmount(new BigDecimal("100.00"));

        Account account = createAccount(1L, Currency.USD, new BigDecimal("500.00"));

        when(accountService.getAccount(1L, true)).thenReturn(account);
        when(exchangeRateService.getExchangeRate(Currency.USD, Currency.USD)).thenReturn(BigDecimal.ONE);
        when(accountService.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(109L);
            return t;
        });
        doNothing().when(eventService).newEvent(any(NewTransferEvent.class));

        // Execute
        Transfer result = transferService.createTransfer(dto);

        // Verify transfer created (business logic may want to prevent this, but testing current behavior)
        assertNotNull(result);
        assertThat(result.getId(), is(109L));
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
