package hu.imregergo.paymentservice.transfer.exception;

public class ExchangeRateApiException extends RuntimeException {
    public ExchangeRateApiException(String message) {
        super(message);
    }
}
