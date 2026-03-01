package hu.imregergo.paymentservice.transfer.exception;

public class NotEnoughBalanceException extends RuntimeException {
    public NotEnoughBalanceException(
    ) {
        super("Not enough balance");
    }
}
