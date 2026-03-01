package hu.imregergo.paymentservice.eventsystem.repository;

import hu.imregergo.paymentservice.eventsystem.entity.OutboxMessage;
import hu.imregergo.paymentservice.eventsystem.entity.OutboxMessageStatus;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends CrudRepository<OutboxMessage, Long> {

    @Query("SELECT * FROM outbox_message WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 100 FOR UPDATE SKIP LOCKED")
    List<OutboxMessage> findLast100PendingAndLock();

    @Modifying
    @Query("UPDATE outbox_message SET status = :status, updated_at = NOW() WHERE id IN (:ids)")
    void updateStateByIds(List<Long> ids, OutboxMessageStatus status);
}
