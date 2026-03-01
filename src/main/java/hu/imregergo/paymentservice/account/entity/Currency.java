package hu.imregergo.paymentservice.account.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

// For simplicity, I use a fixed exchange rate for each currency. Note that in a real application I wouldnt use an enum for this but an entity.
@AllArgsConstructor
@Getter
public enum Currency {
    EUR(BigDecimal.ONE),
    USD(new BigDecimal("1.1")),
    GBP(new BigDecimal("0.9")),
    HUF(new BigDecimal("350")),
    ;

    private final BigDecimal exchangeRate;
}
