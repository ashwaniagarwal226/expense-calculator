package com.expense.calculator.repository;

import com.expense.calculator.domain.EpnxTransaction;
import com.expense.calculator.hdfc.DTOs.TransactionSummaryDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<EpnxTransaction,Long> {
    @Query("SELECT e FROM EpnxTransaction e WHERE MONTH(e.transactionDate) = MONTH(:transactionDate) AND YEAR(e.transactionDate) = YEAR(:transactionDate)")
    List<EpnxTransaction> findByMonthAndYear(@Param("transactionDate") LocalDateTime transactionDate);
    @Query(value = "SELECT ROUND(SUM(withdraw_amt), 2)  as withdraw_amt," +
            " transaction_type, FORMATDATETIME(transaction_date, 'MMMM') " +
            "as trans_month,YEAR(transaction_date)  as trans_year\n" +
            "FROM EPNX_TRANSACTIONS \n" +
            "group by transaction_type , trans_month,trans_year order by  trans_month,trans_year",nativeQuery = true)
    List<Object[]> findTransactionSummaries();

}
