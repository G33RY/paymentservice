package hu.imregergo.paymentservice.transfer.repository;

import hu.imregergo.paymentservice.transfer.entity.Transfer;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface TransferRepository extends ListCrudRepository<Transfer, Long> {

    @Query("SELECT COUNT(*) FROM transfer WHERE from_account = :accountId AND created_at > :since")
    int countRecentTransfers(@Param("accountId") Long accountId, @Param("since") Instant since);
}
