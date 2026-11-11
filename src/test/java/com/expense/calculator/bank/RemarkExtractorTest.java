package com.expense.calculator.bank;

import com.expense.calculator.config.ExpenseProperties;
import com.expense.calculator.config.ExpenseProperties.BankProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RemarkExtractorTest {

    final RemarkExtractor extractor = new RemarkExtractor(new ExpenseProperties(
            new ExpenseProperties.Ai(true, 25, true, 0.8, "gemini", ""),
            List.of(new BankProfile("HDFC", "HDFC", Map.of(), List.of(), List.of(), "^UPI-.*-\\d{12}-(?<remark>[^-].*)$"),
                    new BankProfile("SBI", "SBI", Map.of(), List.of(), List.of(),
                            "UPI/(?:DR|CR)/\\d+/[^/]*/[^/]*/[^/]*@[^/]*/(?<remark>[^/]+)")),
            List.of("UPI", "PAYMENT FROM PHONE", "PAYMENT", "NA"),
            new ExpenseProperties.Seed(List.of(), List.of())));

    @Test
    void extractsTheNoteTypedInTheUpiApp() {
        assertThat(extractor.extract("HDFC", "UPI-RAHUL SINGH-Q218750349@YBL-YESB0YBLUPI-907923059936-TAXI")).isEqualTo("TAXI");
        assertThat(extractor.extract("HDFC", "UPI-GUPTA PHARMACY-9748895894@AXISBANK-UTIB0002081-259763607747-meds")).isEqualTo("MEDS");
        assertThat(extractor.extract("SBI", "TO TRANSFER-UPI/DR/400512345678/BIGBASKET/YESB/bigbasket@ybl/groceries")).isEqualTo("GROCERIES");
    }

    @Test
    void ignoresBoilerPlateAndOrderIds() {
        assertThat(extractor.extract("HDFC", "UPI-RAHUL SINGH-Q218750349@YBL-YESB0YBLUPI-907923059936-PAYMENT FROM PHONE")).isEmpty();
        assertThat(extractor.extract("HDFC", "UPI-SWIGGY-swiggy@icici-ICIC0DC0099-400212345678-UPI")).isEmpty();
        assertThat(extractor.extract("HDFC", "UPI-X-x@ybl-YESB0YBLUPI-907923059936-OIDCDJPMAN98199578")).isEmpty();
        assertThat(extractor.extract("HDFC", "POS 512345XXXXXX1234 AMAZON PAY INDIA")).isEmpty();
        assertThat(extractor.extract("AXIS", "UPI/P2A/123/X/Y/taxi")).isEmpty(); // no pattern configured in this test
    }
}
