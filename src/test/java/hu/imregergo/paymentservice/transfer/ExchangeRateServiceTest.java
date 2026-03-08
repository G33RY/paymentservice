package hu.imregergo.paymentservice.transfer;

import hu.imregergo.paymentservice.account.entity.Currency;
import hu.imregergo.paymentservice.transfer.exception.ExchangeRateApiException;
import hu.imregergo.paymentservice.transfer.service.ExchangeRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExchangeRateServiceTest {

    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        // deterministic: always success + no sleep
        exchangeRateService = new ExchangeRateService(
                () -> 0.9,
                ms -> {
                }
        );
    }

    @Test
    void testGetExchangeRate_sameCurrency_returnsOne() {
        BigDecimal rate = exchangeRateService.getExchangeRate(Currency.USD, Currency.USD);
        assertThat(rate, is(BigDecimal.ONE));
    }

    @Test
    void testGetExchangeRate_differentCurrencies_returnsRate() {
        BigDecimal rate = exchangeRateService.getExchangeRate(Currency.USD, Currency.EUR);

        BigDecimal expected = Currency.EUR.getExchangeRate()
                .divide(Currency.USD.getExchangeRate(), 4, RoundingMode.HALF_UP);

        assertThat(rate, is(expected));
    }

    @Test
    void testGetExchangeRate_differentCurrencies_failure_throwsExchangeRateApiException() {
        ExchangeRateService failing = new ExchangeRateService(
                () -> 0.05, // < 0.1 => failure
                ms -> {
                }
        );

        assertThrows(ExchangeRateApiException.class,
                () -> failing.getExchangeRate(Currency.USD, Currency.EUR));
    }

    @Test
    void testFallback_throwsExchangeRateApiException() {
        ExchangeRateApiException exception = assertThrows(
                ExchangeRateApiException.class,
                () -> exchangeRateService.fallbackExchangeRate(
                        Currency.USD, Currency.EUR, new RuntimeException("circuit open"))
        );

        assertThat(exception.getMessage(), containsString("Exchange rate service unavailable"));
        assertThat(exception.getMessage(), containsString("circuit open"));
    }
}
