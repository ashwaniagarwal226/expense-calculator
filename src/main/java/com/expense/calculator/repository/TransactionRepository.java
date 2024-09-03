package com.expense.calculator.repository;

import com.expense.calculator.domain.EpnxTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<EpnxTransaction,Long> {
    List<EpnxTransaction> findAllByrefNumIn(List<String> refNums);

}
