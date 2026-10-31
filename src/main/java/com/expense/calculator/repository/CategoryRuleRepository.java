package com.expense.calculator.repository;

import com.expense.calculator.domain.CategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    @Modifying
    @Query("DELETE FROM CategoryRule r WHERE r.profileId = :profileId")
    int deleteByProfileId(Long profileId);
}
