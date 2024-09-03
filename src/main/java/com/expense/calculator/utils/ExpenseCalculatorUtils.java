package com.expense.calculator.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ExpenseCalculatorUtils {

    public static boolean checkAllStars(List<String> row) {
        return row.stream()
                .allMatch(str -> str.chars().allMatch(c -> c == '*'));
    }

    public static boolean containsLabels(List<String> row){
        List<String> expectedValues = Arrays.asList("Date", "Narration", "Chq./Ref.No.", "Value Dt", "Withdrawal Amt.", "Deposit Amt.", "Closing Balance");

        List<String> missingValues = expectedValues.stream()
                .filter(value -> !row.contains(value))
                .collect(Collectors.toList());

        return missingValues.isEmpty();
    }
    public static LocalDateTime convertToHDFCDateToLocalDateTime(String date){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
        LocalDate localDate = LocalDate.parse(date, formatter);
        return localDate.atStartOfDay();
    }
}
