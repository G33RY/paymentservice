package hu.imregergo.paymentservice.transfer.controller;

import hu.imregergo.paymentservice.idempotency.annotation.IdempotentEndpoint;
import hu.imregergo.paymentservice.transfer.dto.CreateTransferDto;
import hu.imregergo.paymentservice.transfer.dto.TransferResponse;
import hu.imregergo.paymentservice.transfer.entity.Transfer;
import hu.imregergo.paymentservice.transfer.mapper.TransferMapper;
import hu.imregergo.paymentservice.transfer.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public TransferResponse createTransfer(@Valid @RequestBody CreateTransferDto dto) {
        Transfer transfer = transferService.createTransfer(dto);
        return TransferMapper.toResponse(transfer);
    }
}
