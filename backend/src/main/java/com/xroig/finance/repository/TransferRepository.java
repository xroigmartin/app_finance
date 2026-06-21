package com.xroig.finance.repository;

import com.xroig.finance.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findAllByOrderByDateDescIdDesc();

    List<Transfer> findByDateBetween(java.time.LocalDate from, java.time.LocalDate to);

    @Query("""
            select t from Transfer t
            where t.date >= :from and t.date <= :to
              and (:accountId is null or t.fromAccount.id = :accountId or t.toAccount.id = :accountId)
            order by t.date desc, t.id desc
            """)
    List<Transfer> search(@Param("from") java.time.LocalDate from,
                          @Param("to") java.time.LocalDate to,
                          @Param("accountId") Long accountId);

    boolean existsByFromAccountIdOrToAccountId(Long fromAccountId, Long toAccountId);

    @Query("select coalesce(sum(t.amount), 0) from Transfer t where t.toAccount.id = :accountId and t.date <= :until")
    BigDecimal totalInUntil(@Param("accountId") Long accountId, @Param("until") java.time.LocalDate until);

    @Query("select coalesce(sum(t.amount), 0) from Transfer t where t.fromAccount.id = :accountId and t.date <= :until")
    BigDecimal totalOutUntil(@Param("accountId") Long accountId, @Param("until") java.time.LocalDate until);
}
