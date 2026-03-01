package hu.imregergo.paymentservice.transfer.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferResponse {

    private Long id;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private BigDecimal rate;

}
