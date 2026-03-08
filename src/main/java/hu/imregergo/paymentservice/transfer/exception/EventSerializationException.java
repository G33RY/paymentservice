package hu.imregergo.paymentservice.transfer.exception;

public class EventSerializationException extends RuntimeException {
    public EventSerializationException(Throwable cause) {
        super("Failed to serialize transfer event", cause);
    }
}
