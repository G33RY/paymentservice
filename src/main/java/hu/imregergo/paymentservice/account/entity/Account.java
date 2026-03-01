package hu.imregergo.paymentservice.account.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

@Data
public class Account {

    @Id
    private Long id;

    private BigDecimal balance;
    private BigDecimal initialBalance;

    private Currency currency;

    private Instant createdAt = Instant.now();
    private Instant updatedAt;
}
