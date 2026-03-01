package hu.imregergo.paymentservice.idempotency.exception;

import hu.imregergo.paymentservice.idempotency.entity.IdempotencyStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class IdempotentItemExists extends RuntimeException {
    private final String key;
    private final IdempotencyStatus status;
    private final Integer responseStatus;
    private final String responseJson;
}
