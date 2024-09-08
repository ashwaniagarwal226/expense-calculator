package com.expense.calculator.service.excel.impl;

import com.expense.calculator.domain.EpnxTransaction;
import com.expense.calculator.repository.TransactionRepository;
import com.expense.calculator.service.excel.ExcelReadSaveService;
import com.expense.calculator.utils.ExpenseCalculatorUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.expense.calculator.utils.TransactionUtils.getTransactionType;

@Service
public class ExcelReadSaveServiceImpl implements ExcelReadSaveService {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy");
    private static final String TYPE_API = "API";

    @Autowired
    private TransactionRepository transactionRepository;


    @Override
    @Transactional
    public void readAndSaveExcelFile(MultipartFile file) throws IOException {
        List<List<String>> data = new ArrayList<>();

        try (InputStream fis = file.getInputStream();
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
        List<String> refnums = convertDataToTransactionDomain(data).stream().map(EpnxTransaction::getRefNum).collect(Collectors.toList());
        List<EpnxTransaction> transactionList = transactionRepository.findAllByrefNumIn(refnums);
        if (transactionList != null && transactionList.size() > 0 && !ExpenseCalculatorUtils.checkIfAllZero(transactionList.get(0).getRefNum())) {
            return;
        }
        transactionRepository.saveAll(convertDataToTransactionDomain(data));
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

    private List<EpnxTransaction> convertDataToTransactionDomain(List<List<String>> data) {
        boolean isFirstStarList = false;
        List<EpnxTransaction> transactionList = new ArrayList<>();

        for (List<String> row : data) {
            if (ExpenseCalculatorUtils.checkAllStars(row) && !isFirstStarList) {
                isFirstStarList = true;
            } else if (ExpenseCalculatorUtils.checkAllStars(row) && isFirstStarList) {
                break;
            } else {
                if (!ExpenseCalculatorUtils.containsLabels(row)) {
                    EpnxTransaction transaction = convertRowToDomain(row);
                    transactionList.add(transaction);
                }
            }
        }
        return transactionList;
    }

    private EpnxTransaction convertRowToDomain(List<String> row) {
        LocalDateTime transactionDate = ExpenseCalculatorUtils.convertToHDFCDateToLocalDateTime(row.get(0));
        String transactonName = row.get(1);
        String chqRefNo = row.get(2);
        LocalDateTime valueDate = ExpenseCalculatorUtils.convertToHDFCDateToLocalDateTime(row.get(3));
        BigDecimal withdrawalAmt = "".trim().equalsIgnoreCase(row.get(4)) ? null : new BigDecimal(row.get(4));
        BigDecimal depositAmt = "".trim().equalsIgnoreCase(row.get(5)) ? null : new BigDecimal(row.get(5));
        //BigDecimal closingBalance = "".trim().equalsIgnoreCase(row.get(6)) ? null : new BigDecimal(row.get(6));

        return new EpnxTransaction(null, transactonName, getTransactionType(transactonName),
                transactionDate, chqRefNo, valueDate, withdrawalAmt, depositAmt, LocalDateTime.now(),
                LocalDateTime.now(), TYPE_API);
    }


}
