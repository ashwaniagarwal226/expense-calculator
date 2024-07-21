package com.expense.calculator.domain;


import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "EPNX_TRANSACTIONS")
public record EpnxTransaction(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "TRANS_ID")
        Long transId,

        @Column(name = "TRANSACTION_NAME", nullable = false, length = 10000)
        String transactionName,

        @Column(name = "TRANSACTION_DATE", nullable = false)
        LocalDateTime transactionDate,

        @Column(name = "REF_NUM", nullable = false, length = 500, unique = true)
        String refNum,

        @Column(name = "VAL_DATE", nullable = false)
        LocalDateTime valDate,

        @Column(name = "WITHDRAW_AMT", precision = 19, scale = 4)
        BigDecimal withdrawAmt,

        @Column(name = "DEPOSIT_AMT", precision = 19, scale = 4)
        BigDecimal depositAmt,

        @Column(name = "CREATE_DT")
        LocalDateTime createDt,

        @Column(name = "UPDATED_DT")
        LocalDateTime updatedDt,

        @Column(name = "CREATED_BY", length = 50)
        String createdBy
) {}