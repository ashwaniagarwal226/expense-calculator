package com.expense.calculator.bank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantExtractorTest {

    @Test
    void extractsPayeeFromCommonNarrations() {
        assertThat(MerchantExtractor.extract("UPI-SWIGGY LIMITED-swiggy.upi@icici-ICIC0DC0099-412345678901-PAYMENT"))
                .isEqualTo("SWIGGY LIMITED");
        assertThat(MerchantExtractor.extract("POS 512345XXXXXX1234 AMAZON PAY INDIA")).isEqualTo("AMAZON PAY INDIA");
        assertThat(MerchantExtractor.extract("UPI/DR/400512345678/BIGBASKET/YESB/bigbasket@ybl")).isEqualTo("BIGBASKET");
        assertThat(MerchantExtractor.extract("NEFT CR-HERE SOLUTIONS INDIA PVT LTD-SALARY"))
                .isEqualTo("HERE SOLUTIONS INDIA PVT LTD");
        assertThat(MerchantExtractor.extract("")).isEqualTo("UNKNOWN");
    }
}
