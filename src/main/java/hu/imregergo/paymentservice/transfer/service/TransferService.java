package hu.imregergo.paymentservice.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import hu.imregergo.paymentservice.account.entity.Account;
import hu.imregergo.paymentservice.account.exception.AccountNotFoundException;
import hu.imregergo.paymentservice.account.service.AccountService;
import hu.imregergo.paymentservice.eventsystem.service.EventService;
import hu.imregergo.paymentservice.idempotency.service.IdempotencyService;
import hu.imregergo.paymentservice.transfer.dto.CreateTransferDto;
import hu.imregergo.paymentservice.transfer.dto.NewTransferEvent;
import hu.imregergo.paymentservice.transfer.dto.TransferResponse;
import hu.imregergo.paymentservice.transfer.entity.Transfer;
import hu.imregergo.paymentservice.transfer.exception.ExchangeRateApiException;
import hu.imregergo.paymentservice.transfer.exception.NotEnoughBalanceException;
import hu.imregergo.paymentservice.transfer.mapper.TransferMapper;
import hu.imregergo.paymentservice.transfer.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;
    private final EventService eventService;


    @Transactional
    public Transfer createTransfer(CreateTransferDto createTransferDto) throws AccountNotFoundException, NotEnoughBalanceException, ExchangeRateApiException, JsonProcessingException {
        Transfer transfer = new Transfer();

        Account fromAccount = accountService.getAccount(createTransferDto.getFromAccountId(), true);
        transfer.setFromAccount(AggregateReference.to(fromAccount.getId()));

        Account toAccount = accountService.getAccount(createTransferDto.getToAccountId(), true);
        transfer.setToAccount(AggregateReference.to(toAccount.getId()));

        transfer.setAmount(createTransferDto.getAmount());
        transfer.setRate(exchangeRateService.getExchangeRate(fromAccount.getCurrency(), toAccount.getCurrency()));

        if(fromAccount.getBalance().compareTo(createTransferDto.getAmount()) < 0){
            throw new NotEnoughBalanceException();
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(createTransferDto.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(createTransferDto.getAmount().multiply(transfer.getRate())));

        accountService.save(fromAccount);
        accountService.save(toAccount);

        transfer = transferRepository.save(transfer);
        eventService.newEvent(TransferMapper.toNewTransferEvent(transfer));

        return transfer;
    }




}
