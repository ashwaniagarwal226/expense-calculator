package com.expense.calculator;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Synthetic statements that mimic the real layouts. */
public final class TestStatements {

    private TestStatements() {
    }

    /** HDFC .xls: bank header block, column header, star separator, data, star separator, summary. */
    public static byte[] hdfcXls(String[][] data) throws IOException {
        try (Workbook wb = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet();
            int r = 0;
            row(s, r++, "HDFC BANK Ltd.", "", "Page No .: 1");
            row(s, r++, "MR TEST USER", "", "Statement From : 01/01/24 To : 31/01/24");
            r++;
            row(s, r++, "Date", "Narration", "Chq./Ref.No.", "Value Dt", "Withdrawal Amt.", "Deposit Amt.", "Closing Balance");
            row(s, r++, "********", "**********", "*******", "********", "********", "********", "********");
            for (String[] d : data) row(s, r++, d);
            row(s, r++, "********", "**********", "*******", "********", "********", "********", "********");
            row(s, r++, "STATEMENT SUMMARY :-");
            row(s, r++, "Opening Balance", "Dr Count", "Cr Count", "Debits", "Credits", "Closing Bal");
            row(s, r, "10000.00", "2", "1", "700.00", "50000.00", "59300.00");
            wb.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] sbiCsv() {
        return """
                State Bank of India
                Account Number,00000012345
                
                Txn Date,Value Date,Description,Ref No./Cheque No.,Debit,Credit,Balance
                5 Jan 2024,5 Jan 2024,TO TRANSFER-UPI/DR/400512345678/BIGBASKET/YESB/bigbasket@ybl,TRANSFER TO 4897691162091,"1,250.00",,"48,750.00"
                ,,/Payment for groceries,,,,
                7 Jan 2024,7 Jan 2024,BY TRANSFER-NEFT*ACME CORP SALARY JAN,,,"90,000.00","1,38,750.00"
                9 Jan 2024,9 Jan 2024,TO TRANSFER-UPI/DR/400912345678/MYSTERY STORE/HDFC/mystery@okhdfc,,450.00,,"1,38,300.00"
                """.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] kotakCsv() {
        return """
                Kotak Mahindra Bank
                Sl. No.,Transaction Date,Value Date,Description,Chq / Ref No.,Amount,Dr / Cr,Balance
                1,02-01-2024,02-01-2024,UPI/UBER INDIA/401234567890/Ride,UPI-401234567890,312.50,DR,9687.50
                2,03-01-2024,03-01-2024,INTEREST CREDIT,,25.00,CR,9712.50
                """.getBytes(StandardCharsets.UTF_8);
    }

    private static void row(Sheet s, int idx, String... values) {
        Row row = s.createRow(idx);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }
}
