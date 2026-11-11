package com.expense.calculator.bank;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns an uploaded file (xls, xlsx or csv) into a grid of trimmed strings. No bank knowledge here:
 * date cells are rendered as ISO yyyy-MM-dd, every other cell as the text the bank put there.
 */
@Component
public class StatementReader {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    public List<List<String>> read(InputStream in, String fileName) throws IOException {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv") || name.endsWith(".txt")) {
            return readCsv(in);
        }
        return readWorkbook(in);
    }

    private List<List<String>> readWorkbook(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                // keep column positions stable even when leading cells are missing
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    values.add(cell == null ? "" : cellText(cell, formatter));
                }
                rows.add(values);
            }
        } catch (org.apache.poi.EmptyFileException | org.apache.poi.UnsupportedFileFormatException e) {
            throw new StatementParseException("Not a readable Excel file: " + e.getMessage(), e);
        }
        return rows;
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().format(ISO);
                }
                // "General" format: avoid scientific notation for long reference numbers
                yield "General".equals(cell.getCellStyle().getDataFormatString())
                        ? BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString()
                        : formatter.formatCellValue(cell).trim();
            }
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private List<List<String>> readCsv(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder pending = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (pending != null) {
                    pending.append('\n').append(line);
                    line = pending.toString();
                }
                if (countQuotes(line) % 2 != 0) { // quoted field spans lines
                    pending = new StringBuilder(line);
                    continue;
                }
                pending = null;
                rows.add(parseCsvLine(stripBom(line)));
            }
        }
        return rows;
    }

    private static int countQuotes(String s) {
        return (int) s.chars().filter(ch -> ch == '"').count();
    }

    private static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
    }

    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }
}
