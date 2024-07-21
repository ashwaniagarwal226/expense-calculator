package com.expense.calculator.service.excel.impl;

import com.expense.calculator.service.excel.ExcelReadService;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelReadServiceImpl implements ExcelReadService {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy");

    @Override
    public List<List<String>> readExcelFile(String filePath) throws IOException {
        List<List<String>> data = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            boolean tableStarted = false;

            for (Row row : sheet) {
                List<String> rowData = new ArrayList<>();
                if (!tableStarted) {
                    for (Cell cell : row) {
                        if (cell.getStringCellValue().equalsIgnoreCase("Date")) {
                            tableStarted = true;
                            break;
                        }
                    }
                }

                if (tableStarted) {
                    for (Cell cell : row) {
                        switch (cell.getCellType()) {
                            case STRING:
                                String cellValue = cell.getStringCellValue();
                                if (isValidDate(cellValue)) {
                                    rowData.add(parseDate(cellValue));
                                } else {
                                    rowData.add(cellValue);
                                }
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    rowData.add(dateFormat.format(cell.getDateCellValue()));
                                } else {
                                    rowData.add(String.valueOf(cell.getNumericCellValue()));
                                }
                                break;
                            case BOOLEAN:
                                rowData.add(String.valueOf(cell.getBooleanCellValue()));
                                break;
                            case FORMULA:
                                rowData.add(cell.getCellFormula());
                                break;
                            case BLANK:
                                rowData.add("");
                                break;
                            default:
                                rowData.add("");
                        }
                    }
                    if (!rowData.isEmpty()) {
                        data.add(rowData);
                    }
                }
            }
        }
        return data;
    }
    private boolean isValidDate(String dateStr) {
        try {
            dateFormat.parse(dateStr);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    private String parseDate(String dateStr) {
        try {
            return dateFormat.format(dateFormat.parse(dateStr));
        } catch (ParseException e) {
            return dateStr;
        }
    }
}
