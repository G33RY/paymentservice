package hu.imregergo.paymentservice.transfer.service;

import hu.imregergo.paymentservice.account.entity.Currency;
import hu.imregergo.paymentservice.transfer.exception.ExchangeRateApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.DoubleSupplier;
import java.util.function.LongConsumer;

@Service
public class ExchangeRateService {

    private final DoubleSupplier randomSupplier;
    private final LongConsumer sleeper;

    public ExchangeRateService() {
        this(Math::random, ms -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                throw new ExchangeRateApiException("Failed to get exchange rate");
            }
        });
    }

    // For tests / custom wiring
    public ExchangeRateService(DoubleSupplier randomSupplier, LongConsumer sleeper) {
        this.randomSupplier = randomSupplier;
        this.sleeper = sleeper;
    }

    @Retry(name = "exchangeRate")
    @CircuitBreaker(name = "exchangeRate", fallbackMethod = "fallbackExchangeRate")
    public BigDecimal getExchangeRate(Currency currencyFrom, Currency currencyTo) {
        if (currencyFrom.equals(currencyTo)) {
            return BigDecimal.ONE;
        }

        boolean isSuccess = randomSupplier.getAsDouble() > 0.1;
        sleeper.accept(1000L); // mock api call

        if (!isSuccess) {
            throw new ExchangeRateApiException("Failed to get exchange rate");
        }

        // mock exchange rate conversion
        return currencyTo.getExchangeRate().divide(currencyFrom.getExchangeRate(), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal fallbackExchangeRate(Currency currencyFrom, Currency currencyTo, Throwable t) {
        throw new ExchangeRateApiException("Exchange rate service unavailable - circuit open");
    }
}
