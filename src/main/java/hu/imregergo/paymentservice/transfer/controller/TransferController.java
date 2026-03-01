package hu.imregergo.paymentservice.transfer.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import hu.imregergo.paymentservice.account.exception.AccountNotFoundException;
import hu.imregergo.paymentservice.common.exception.ApiError;
import hu.imregergo.paymentservice.idempotency.annotation.IdempotentEndpoint;
import hu.imregergo.paymentservice.transfer.dto.CreateTransferDto;
import hu.imregergo.paymentservice.transfer.dto.TransferResponse;
import hu.imregergo.paymentservice.transfer.entity.Transfer;
import hu.imregergo.paymentservice.transfer.exception.ExchangeRateApiException;
import hu.imregergo.paymentservice.transfer.exception.NotEnoughBalanceException;
import hu.imregergo.paymentservice.transfer.mapper.TransferMapper;
import hu.imregergo.paymentservice.transfer.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @IdempotentEndpoint(expireSeconds = 300)
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse toTransferResponse(@Valid @RequestBody CreateTransferDto dto) throws JsonProcessingException {
        Transfer transfer = transferService.createTransfer(dto);
        return TransferMapper.toResponse(transfer);
    }


    @ExceptionHandler({AccountNotFoundException.class, NotEnoughBalanceException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler({ExchangeRateApiException.class})
    public ResponseEntity<ApiError> handleExchangeRateApiException(ExchangeRateApiException ex) {
        return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body(new ApiError(ex.getMessage()));
    }
}
