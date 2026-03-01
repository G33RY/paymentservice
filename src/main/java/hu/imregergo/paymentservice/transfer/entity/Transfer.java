package hu.imregergo.paymentservice.transfer.entity;

import hu.imregergo.paymentservice.account.entity.Account;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

@Data
public class Transfer {

    @Id
    private Long id;

    private AggregateReference<Account, Long> fromAccount;
    private AggregateReference<Account, Long> toAccount;

    private Instant createdAt = Instant.now();
    private BigDecimal amount;
    private BigDecimal rate;
}
