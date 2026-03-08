package hu.imregergo.paymentservice.transfer.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Transfer rate limit exceeded. Maximum 10 transfers per minute per account.");
    }
}
