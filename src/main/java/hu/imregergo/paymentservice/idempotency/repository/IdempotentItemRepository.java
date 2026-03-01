package hu.imregergo.paymentservice.idempotency.repository;

import hu.imregergo.paymentservice.idempotency.entity.IdempotencyStatus;
import hu.imregergo.paymentservice.idempotency.entity.IdempotentItem;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;

@Repository
public interface IdempotentItemRepository extends CrudRepository<IdempotentItem, String> {

    @Modifying
    @Query("""
        INSERT INTO idempotent_item (id, status, expires_at)
        VALUES (:id, :status, :expiresAt)
    """)
    void insert(String id, IdempotencyStatus status, Instant expiresAt);
}
