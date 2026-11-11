package com.expense.calculator.repository;

import com.expense.calculator.domain.CategorySource;
import com.expense.calculator.domain.EpnxTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<EpnxTransaction, Long>,
        JpaSpecificationExecutor<EpnxTransaction> {

    List<EpnxTransaction> findByProfileIdAndBankAndRefNumIn(Long profileId, String bank, Collection<String> refNums);

    List<EpnxTransaction> findByCategorySourceNot(CategorySource source);

    List<EpnxTransaction> findByProfileIdAndCategorySourceNot(Long profileId, CategorySource source);

    List<EpnxTransaction> findByProfileId(Long profileId);

    long countByProfileId(Long profileId);

    @Modifying
    @Query("DELETE FROM EpnxTransaction t WHERE t.profileId = :profileId")
    int deleteByProfileId(Long profileId);
}
