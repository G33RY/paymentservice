package hu.imregergo.paymentservice.transfer.service;

import hu.imregergo.paymentservice.account.entity.Currency;
import hu.imregergo.paymentservice.transfer.exception.ExchangeRateApiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeoutException;

@Service
public class ExchangeRateService {

    public BigDecimal getExchangeRate(Currency currencyFrom, Currency currencyTo) throws ExchangeRateApiException {
        if(currencyFrom.equals(currencyTo)) {
            return BigDecimal.ONE;
        }
        boolean isSuccess = Math.random() > 0.1;
        try{
            Thread.sleep(1000); //mock api call
        } catch (InterruptedException e) {
            throw new ExchangeRateApiException("Failed to get exchange rate");
        }
        if(!isSuccess) {
            throw new ExchangeRateApiException("Failed to get exchange rate");
        }

        //mock exchange rate conversion
        return currencyTo.getExchangeRate().divide(currencyFrom.getExchangeRate(), 4, RoundingMode.HALF_UP);
    }
}
