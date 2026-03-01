package hu.imregergo.paymentservice.idempotency.service;

import hu.imregergo.paymentservice.idempotency.entity.IdempotencyStatus;
import hu.imregergo.paymentservice.idempotency.entity.IdempotentItem;
import hu.imregergo.paymentservice.idempotency.exception.IdempotentItemExists;
import hu.imregergo.paymentservice.idempotency.repository.IdempotentItemRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;

@Service
public class IdempotencyService {
    public static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private final IdempotentItemRepository idempotentItemRepository;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyService(IdempotentItemRepository idempotentItemRepository, @Qualifier("requiresNewTxTemplate") TransactionTemplate transactionTemplate) {
        this.idempotentItemRepository = idempotentItemRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public void create(String key, Instant expiresAt) throws IdempotentItemExists {
        try{
            transactionTemplate.execute(status -> {
                idempotentItemRepository.insert(key, IdempotencyStatus.PROCESSING, expiresAt);
                return null;
            });
        }catch (TransactionException | DataIntegrityViolationException e) {
            IdempotentItem existing = idempotentItemRepository.findById(key).orElse(null);
            if(existing != null) {
                throw new IdempotentItemExists(key, existing.getStatus(), existing.getResponseStatus(), existing.getResponseJson());
            }
            throw e;
        }
    }


    /**
     * Updates the status and response JSON of the idempotent transfer record with the given key to COMPLETED.
     * If no record with the given key exists, it does nothing.
     * @param key the unique key for the idempotent transfer
     * @param responseJson the response JSON to be stored in the record
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, String responseJson, Integer status)  {
        IdempotentItem transfer = idempotentItemRepository.findById(key).orElse(null);
        if(transfer == null) {
            return;
        }
        transfer.setStatus(IdempotencyStatus.COMPLETED);
        transfer.setResponseJson(responseJson);
        transfer.setResponseStatus(status);
        idempotentItemRepository.save(transfer);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String key) {
        idempotentItemRepository.deleteById(key);
    }
}
