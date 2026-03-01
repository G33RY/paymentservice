package hu.imregergo.paymentservice.idempotency.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.OffsetDateTime;

@Data
public class IdempotentItem {

    @Id
    private String id;

    private String responseJson;
    private Integer responseStatus;

    private IdempotencyStatus status;
    private Instant expiresAt;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
