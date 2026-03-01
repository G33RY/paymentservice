package hu.imregergo.paymentservice.eventsystem.entity;


import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Data
public class OutboxMessage {
    @Id
    private Long id;

    private OutboxMessageStatus status;
    private String payload;
    private Instant createdAt = Instant.now();
    private Instant updatedAt;
}
