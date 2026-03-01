package hu.imregergo.paymentservice.account;

import hu.imregergo.paymentservice.account.dto.CreateAccountDto;
import hu.imregergo.paymentservice.account.entity.Account;
import hu.imregergo.paymentservice.account.entity.Currency;
import hu.imregergo.paymentservice.account.exception.AccountNotFoundException;
import hu.imregergo.paymentservice.account.repository.AccountRepository;
import hu.imregergo.paymentservice.account.service.AccountService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    AccountRepository accountRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository);
    }


    @Test
    void testCreateAccount_save_successful() {
        CreateAccountDto createAccountDto = new CreateAccountDto(Currency.EUR, new BigDecimal("100.00"));

        // Mock the save method to return the account that was passed in
        when(accountRepository.save(Mockito.any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Call the method under test
        Account account = accountService.createAccount(createAccountDto);

        // Verify that the save method was called with an Account object
        verify(accountRepository).save(Mockito.any(Account.class));

        // Assert that the returned account has the expected properties
        assertThat(account.getCurrency(), Matchers.is(createAccountDto.getCurrency()));
        assertThat(account.getBalance(), Matchers.is(createAccountDto.getInitialBalance()));
    }

    @Test
    void testCreateAccount_setsInitialBalanceCorrectly() {
        BigDecimal initialBalance = new BigDecimal("250.75");
        CreateAccountDto createAccountDto = new CreateAccountDto(Currency.USD, initialBalance);

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setId(1L);
            return acc;
        });

        Account result = accountService.createAccount(createAccountDto);

        assertThat(result.getInitialBalance(), is(initialBalance));
        assertThat(result.getBalance(), is(initialBalance));
        assertThat(result.getCurrency(), is(Currency.USD));
    }

    @Test
    void testCreateAccount_withDifferentCurrencies() {
        for (Currency currency : Currency.values()) {
            CreateAccountDto dto = new CreateAccountDto(currency, new BigDecimal("100.00"));
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Account result = accountService.createAccount(dto);

            assertThat(result.getCurrency(), is(currency));
            verify(accountRepository, times(1)).save(any(Account.class));
            reset(accountRepository);
        }
    }

    @Test
    void testCreateAccount_withZeroBalance() {
        CreateAccountDto createAccountDto = new CreateAccountDto(Currency.EUR, BigDecimal.ZERO);

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.createAccount(createAccountDto);

        assertThat(result.getBalance(), is(BigDecimal.ZERO));
        assertThat(result.getInitialBalance(), is(BigDecimal.ZERO));
    }


    @Test
    void testGetAccount_accountExists_returnsAccount() {
        Long accountId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setCurrency(Currency.USD);
        account.setBalance(new BigDecimal("50.00"));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Account result = accountService.getAccount(accountId);

        assertThat(result, Matchers.is(account));
        verify(accountRepository).findById(accountId);
        verify(accountRepository, never()).findByIdAndLock(any());
    }

    @Test
    void testGetAccount_accountNotFound_throwsException() {
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> accountService.getAccount(accountId)
        );

        assertThat(exception.getMessage(), containsString("999"));
        verify(accountRepository).findById(accountId);
    }

    @Test
    void testGetAccount_withLock_accountExists_returnsAccount() {
        Long accountId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setCurrency(Currency.EUR);
        account.setBalance(new BigDecimal("100.00"));

        when(accountRepository.findByIdAndLock(accountId)).thenReturn(Optional.of(account));

        Account result = accountService.getAccount(accountId, true);

        assertThat(result, is(account));
        verify(accountRepository).findByIdAndLock(accountId);
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void testGetAccount_withLock_accountNotFound_throwsException() {
        Long accountId = 999L;
        when(accountRepository.findByIdAndLock(accountId)).thenReturn(Optional.empty());

        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> accountService.getAccount(accountId, true)
        );

        assertThat(exception.getMessage(), containsString("999"));
        verify(accountRepository).findByIdAndLock(accountId);
    }

    @Test
    void testGetAccount_withoutLock_usesCorrectRepository() {
        Long accountId = 5L;
        Account account = new Account();
        account.setId(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Account result = accountService.getAccount(accountId, false);

        assertThat(result, is(account));
        verify(accountRepository).findById(accountId);
        verify(accountRepository, never()).findByIdAndLock(any());
    }


    @Test
    void testGetAccounts_returnsAllAccounts() {
        Account account1 = new Account();
        account1.setId(1L);
        account1.setCurrency(Currency.USD);
        account1.setBalance(new BigDecimal("100.00"));

        Account account2 = new Account();
        account2.setId(2L);
        account2.setCurrency(Currency.EUR);
        account2.setBalance(new BigDecimal("200.00"));

        Account account3 = new Account();
        account3.setId(3L);
        account3.setCurrency(Currency.GBP);
        account3.setBalance(new BigDecimal("300.00"));

        List<Account> accounts = Arrays.asList(account1, account2, account3);
        when(accountRepository.findAll()).thenReturn(accounts);

        List<Account> result = accountService.getAccounts();

        assertThat(result, hasSize(3));
        assertThat(result, containsInAnyOrder(account1, account2, account3));
        verify(accountRepository).findAll();
    }

    @Test
    void testGetAccounts_emptyList_returnsEmptyList() {
        when(accountRepository.findAll()).thenReturn(Collections.emptyList());

        List<Account> result = accountService.getAccounts();

        assertThat(result, is(empty()));
        verify(accountRepository).findAll();
    }


    @Test
    void testSave_updatesTimestampAndSavesAccount() {
        Account account = new Account();
        account.setId(1L);
        account.setCurrency(Currency.USD);
        account.setBalance(new BigDecimal("150.00"));
        account.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        account.setUpdatedAt(null);

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.save(account);

        assertThat(result.getUpdatedAt(), notNullValue());
        assertThat(result.getUpdatedAt(), greaterThanOrEqualTo(Instant.now().minusSeconds(1)));
        verify(accountRepository).save(account);
    }

    @Test
    void testSave_updatesExistingTimestamp() {
        Instant oldTimestamp = Instant.parse("2026-01-01T00:00:00Z");
        Account account = new Account();
        account.setId(1L);
        account.setCurrency(Currency.EUR);
        account.setBalance(new BigDecimal("200.00"));
        account.setUpdatedAt(oldTimestamp);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.save(account);

        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();

        assertThat(savedAccount.getUpdatedAt(), notNullValue());
        assertThat(savedAccount.getUpdatedAt(), not(oldTimestamp));
        assertThat(savedAccount.getUpdatedAt(), greaterThan(oldTimestamp));
    }

    @Test
    void testSave_preservesOtherFields() {
        Account account = new Account();
        account.setId(10L);
        account.setCurrency(Currency.GBP);
        account.setBalance(new BigDecimal("500.50"));
        account.setInitialBalance(new BigDecimal("500.00"));
        account.setCreatedAt(Instant.parse("2026-01-15T10:30:00Z"));

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.save(account);

        assertThat(result.getId(), is(10L));
        assertThat(result.getCurrency(), is(Currency.GBP));
        assertThat(result.getBalance(), is(new BigDecimal("500.50")));
        assertThat(result.getInitialBalance(), is(new BigDecimal("500.00")));
        assertThat(result.getCreatedAt(), is(Instant.parse("2026-01-15T10:30:00Z")));
    }

}
