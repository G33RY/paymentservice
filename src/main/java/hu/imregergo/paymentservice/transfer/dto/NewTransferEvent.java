package hu.imregergo.paymentservice.transfer.dto;

import hu.imregergo.paymentservice.eventsystem.dto.Event;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class NewTransferEvent extends Event {
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private BigDecimal rate;
}
