package com.expense.calculator.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A person whose statements are tracked (me, spouse, parent ...). Every transaction belongs to one profile. */
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "EPNX_PROFILES")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PROFILE_ID")
    Long id;

    @Column(name = "NAME", nullable = false, length = 60)
    String name;

    @Column(name = "EMOJI", length = 20)
    String emoji;

    @Column(name = "CREATE_DT")
    LocalDateTime createDt;
}
