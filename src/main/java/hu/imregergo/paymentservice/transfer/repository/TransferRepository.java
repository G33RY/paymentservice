package hu.imregergo.paymentservice.transfer.repository;

import hu.imregergo.paymentservice.transfer.entity.Transfer;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferRepository extends ListCrudRepository<Transfer, Long> {
}
