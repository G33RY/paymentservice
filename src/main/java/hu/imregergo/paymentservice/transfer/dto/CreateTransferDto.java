package hu.imregergo.paymentservice.transfer.dto;

import com.fasterxml.jackson.databind.jsontype.impl.MinimalClassNameIdResolver;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;

@Data
public class CreateTransferDto {

    @NotNull
    @Min(1)
    private Long fromAccountId;

    @NotNull
    @Min(1)
    private Long toAccountId;

    @Digits(integer = 19, fraction = 4)
    @NotNull
    @Positive
    private BigDecimal amount;

}
