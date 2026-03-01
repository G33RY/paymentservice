package hu.imregergo.paymentservice.account.dto;

import hu.imregergo.paymentservice.account.entity.Currency;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountResponse {
    private Long id;
    private Currency currency;
    private BigDecimal balance;
}
