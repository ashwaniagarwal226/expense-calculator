package com.expense.calculator.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "EPNX_TRANSACTIONS",
        uniqueConstraints = @UniqueConstraint(name = "UK_TXN_PROFILE_BANK_REF", columnNames = {"PROFILE_ID", "BANK", "REF_NUM"}))
public class EpnxTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TRANS_ID")
    Long transId;

    /** Owner of the statement this line came from. Legacy rows are back-filled to the default profile. */
    @Column(name = "PROFILE_ID")
    Long profileId;

    /** Bank code from banks.yml (HDFC, ICICI, SBI ...). Legacy rows are back-filled to HDFC. */
    @Column(name = "BANK", length = 30)
    String bank;

    @Column(name = "TRANSACTION_NAME", nullable = false, length = 10000)
    String transactionName;

    /** Short payee / merchant name derived from the narration (or returned by Gemini). */
    @Column(name = "MERCHANT", length = 200)
    String merchant;

    /**
     * The note typed in the UPI app ("TAXI", "MEDS"), extracted from the narration with the bank's
     * remark-pattern. Empty string when there is none or it is bank boiler-plate ("PAYMENT FROM PHONE").
     */
    @Column(name = "REMARK", length = 200)
    String remark;

    /** Category code (see {@link Category}). Column name kept for backwards compatibility. */
    @Column(name = "TRANSACTION_TYPE", length = 500)
    String transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY_SOURCE", length = 20)
    CategorySource categorySource;

    @Column(name = "TRANSACTION_DATE", nullable = false)
    LocalDateTime transactionDate;

    @Column(name = "REF_NUM", nullable = false, length = 500)
    String refNum;

    @Column(name = "VAL_DATE", nullable = false)
    LocalDateTime valDate;

    @Column(name = "WITHDRAW_AMT", precision = 19, scale = 4)
    BigDecimal withdrawAmt;

    @Column(name = "DEPOSIT_AMT", precision = 19, scale = 4)
    BigDecimal depositAmt;

    @Column(name = "CLOSING_BALANCE", precision = 19, scale = 4)
    BigDecimal closingBalance;

    @Column(name = "CREATE_DT")
    LocalDateTime createDt;

    @Column(name = "UPDATED_DT")
    LocalDateTime updatedDt;

    @Column(name = "CREATED_BY", length = 50)
    String createdBy;

    public boolean isDebit() {
        return withdrawAmt != null && withdrawAmt.signum() > 0;
    }
}
