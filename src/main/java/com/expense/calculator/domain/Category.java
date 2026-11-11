package com.expense.calculator.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "EPNX_CATEGORIES")
public class Category {

    public static final String OTHERS = "OTHERS";

    /** Stable upper-snake-case code, e.g. SWIGGY, RENT. Stored on transactions. */
    @Id
    @Column(name = "CODE", length = 50)
    String code;

    @Column(name = "DISPLAY_NAME", nullable = false, length = 100)
    String displayName;

    /** Free-text hint sent to Gemini so it understands what belongs in this category. */
    @Column(name = "DESCRIPTION", length = 500)
    String description;

    @Column(name = "ICON", length = 20)
    String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "KIND", nullable = false, length = 20)
    CategoryKind kind;

    /** Essential (needs) vs discretionary (wants) - used by insights and the savings advisor. */
    @Column(name = "ESSENTIAL", nullable = false)
    boolean essential;
}
