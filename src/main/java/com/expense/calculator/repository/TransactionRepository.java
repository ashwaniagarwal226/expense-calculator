package com.expense.calculator.repository;

import com.expense.calculator.domain.EpnxTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<EpnxTransaction,Long> {
    List<EpnxTransaction> findAllByrefNumIn(List<String> refNums);

    @Query("SELECT e FROM EpnxTransaction e WHERE MONTH(e.transactionDate) = MONTH(:transactionDate) AND YEAR(e.transactionDate) = YEAR(:transactionDate)")
    List<EpnxTransaction> findByMonthAndYear(@Param("transactionDate") LocalDateTime transactionDate);

}
