package hu.imregergo.paymentservice.account.repository;


import hu.imregergo.paymentservice.account.entity.Account;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends ListCrudRepository<Account, Long> {

    @Query("""
        SELECT *
        FROM account
        WHERE id = :id
        FOR UPDATE
    """)
    Optional<Account> findByIdAndLock(Long id);
}
