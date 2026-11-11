package com.expense.calculator.bank;

import com.expense.calculator.TestStatements;
import com.expense.calculator.config.ExpenseProperties.BankProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementParserTest {

    static List<BankProfile> profiles;
    final StatementReader reader = new StatementReader();
    final StatementParser parser = new StatementParser();

    @BeforeAll
    static void loadBanksYml() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader().load("banks", new ClassPathResource("banks.yml"))
                .forEach(env.getPropertySources()::addLast);
        profiles = new Binder(ConfigurationPropertySources.get(env))
                .bind("expense.banks", Bindable.listOf(BankProfile.class)).get();
    }

    @Test
    void parsesHdfcXlsAndAutoDetectsBank() throws Exception {
        byte[] xls = TestStatements.hdfcXls(new String[][]{
                {"02/01/24", "UPI-SWIGGY-swiggy.upi@icici-ICIC0DC0099-400212345678-PAYMENT", "0000400212345678", "02/01/24", "450.00", "", "9550.00"},
                {"03/01/24", "NEFT CR-HERE SOLUTIONS INDIA PVT LTD-SALARY", "000000000000000", "03/01/24", "", "50000.00", "59550.00"},
                {"04/01/24", "POS 512345XXXXXX1234 AMAZON PAY INDIA", "0000400412345678", "04/01/24", "250.00", "", "59300.00"},
        });
        List<List<String>> rows = reader.read(new ByteArrayInputStream(xls), "Acct_Statement.xls");

        BankProfile hdfc = parser.detect(rows, profiles).orElseThrow();
        assertThat(hdfc.code()).isEqualTo("HDFC");

        List<ParsedTransaction> txns = parser.parse(rows, hdfc).transactions();
        assertThat(txns).hasSize(3); // star rows and the summary block are ignored
        assertThat(txns.get(0).date()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(txns.get(0).withdrawal()).isEqualByComparingTo("450.00");
        assertThat(txns.get(0).deposit()).isNull();
        assertThat(txns.get(1).deposit()).isEqualByComparingTo("50000");
        assertThat(txns.get(2).balance()).isEqualByComparingTo("59300");
    }

    @Test
    void parsesSbiCsvWithWrappedNarrationAndIndianNumberFormat() throws Exception {
        List<List<String>> rows = reader.read(new ByteArrayInputStream(TestStatements.sbiCsv()), "sbi.csv");
        BankProfile sbi = parser.detect(rows, profiles).orElseThrow();
        assertThat(sbi.code()).isEqualTo("SBI");

        List<ParsedTransaction> txns = parser.parse(rows, sbi).transactions();
        assertThat(txns).hasSize(3);
        assertThat(txns.get(0).narration()).endsWith("/Payment for groceries");
        assertThat(txns.get(0).withdrawal()).isEqualByComparingTo("1250");
        assertThat(txns.get(1).deposit()).isEqualByComparingTo("90000");
        assertThat(txns.get(1).balance()).isEqualByComparingTo(new BigDecimal("138750"));
        assertThat(txns.get(2).date()).isEqualTo(LocalDate.of(2024, 1, 9));
    }

    @Test
    void parsesSingleAmountColumnWithDrCrIndicator() throws Exception {
        List<List<String>> rows = reader.read(new ByteArrayInputStream(TestStatements.kotakCsv()), "kotak.csv");
        BankProfile kotak = parser.detect(rows, profiles).orElseThrow();
        assertThat(kotak.code()).isEqualTo("KOTAK");

        List<ParsedTransaction> txns = parser.parse(rows, kotak).transactions();
        assertThat(txns).hasSize(2);
        assertThat(txns.get(0).withdrawal()).isEqualByComparingTo("312.50");
        assertThat(txns.get(1).deposit()).isEqualByComparingTo("25");
        assertThat(txns.get(1).withdrawal()).isNull();
    }

    @Test
    void wrongBankGivesHelpfulError() throws Exception {
        List<List<String>> rows = reader.read(new ByteArrayInputStream(TestStatements.kotakCsv()), "kotak.csv");
        BankProfile hdfc = profiles.stream().filter(p -> p.code().equals("HDFC")).findFirst().orElseThrow();
        assertThatThrownBy(() -> parser.parse(rows, hdfc))
                .isInstanceOf(StatementParseException.class)
                .hasMessageContaining("HDFC Bank");
    }

    @Test
    void amountsParsing() {
        assertThat(Amounts.parse("1,23,456.78")).isEqualByComparingTo("123456.78");
        assertThat(Amounts.parse("₹ 500.00 Dr")).isEqualByComparingTo("500");
        assertThat(Amounts.parse("0.00")).isNull();
        assertThat(Amounts.parse("")).isNull();
        assertThat(Amounts.parse("abc")).isNull();
    }
}
