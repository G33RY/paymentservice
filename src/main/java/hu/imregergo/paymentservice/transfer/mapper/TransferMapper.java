package hu.imregergo.paymentservice.transfer.mapper;

import hu.imregergo.paymentservice.transfer.dto.NewTransferEvent;
import hu.imregergo.paymentservice.transfer.dto.TransferResponse;
import hu.imregergo.paymentservice.transfer.entity.Transfer;

public class TransferMapper {

    public static TransferResponse toResponse(Transfer transfer) {
        TransferResponse response = new TransferResponse();
        response.setId(transfer.getId());
        response.setFromAccountId(transfer.getFromAccount().getId());
        response.setToAccountId(transfer.getToAccount().getId());
        response.setAmount(transfer.getAmount());
        response.setRate(transfer.getRate());
        return response;
    }

    public static NewTransferEvent toNewTransferEvent(Transfer transfer) {
        NewTransferEvent event = new NewTransferEvent();
        event.setFromAccountId(transfer.getFromAccount().getId());
        event.setToAccountId(transfer.getToAccount().getId());
        event.setAmount(transfer.getAmount());
        return event;
    }
}
