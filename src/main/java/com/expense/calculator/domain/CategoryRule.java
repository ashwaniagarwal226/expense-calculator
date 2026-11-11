package com.expense.calculator.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "EPNX_CATEGORY_RULES")
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RULE_ID")
    Long id;

    @Column(name = "KEYWORD", nullable = false, length = 300)
    String keyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "MATCH_TYPE", nullable = false, length = 10)
    MatchType matchType;

    /** Null for NARRATION (also for rows created before scopes existed). */
    @Enumerated(EnumType.STRING)
    @Column(name = "SCOPE", length = 10)
    RuleScope scope;

    /** Null = applies to every profile (seed rules); otherwise only to this profile's transactions. */
    @Column(name = "PROFILE_ID")
    Long profileId;

    @Column(name = "CATEGORY_CODE", nullable = false, length = 50)
    String categoryCode;

    /** Higher priority rules are evaluated first; ties are broken by longer keyword first. */
    @Column(name = "PRIORITY", nullable = false)
    int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE", nullable = false, length = 10)
    RuleSource source;

    public RuleScope effectiveScope() {
        return scope == null ? RuleScope.NARRATION : scope;
    }

    public boolean appliesTo(Long txnProfileId) {
        return profileId == null || profileId.equals(txnProfileId);
    }

    @Column(name = "CREATE_DT")
    LocalDateTime createDt;
}
