package com.expense.calculator.repository;

import com.expense.calculator.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    boolean existsByNameIgnoreCase(String name);
}
