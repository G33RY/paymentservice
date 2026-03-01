package hu.imregergo.paymentservice.account.dto;

import hu.imregergo.paymentservice.account.entity.Currency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;


@Data
public class CreateAccountDto {
    @NotBlank
    private final Currency currency;

    @Digits(integer = 19, fraction = 4)
    @NotNull
    private final BigDecimal initialBalance;
}
