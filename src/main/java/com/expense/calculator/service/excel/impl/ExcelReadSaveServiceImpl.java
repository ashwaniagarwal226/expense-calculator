package com.expense.calculator.service.excel.impl;

import com.expense.calculator.domain.EpnxTransaction;
import com.expense.calculator.hdfc.DTOs.TransactionSummaryDTO;
import com.expense.calculator.hdfc.DTOs.TransactionTypeSummary;
import com.expense.calculator.hdfc.DTOs.YearMonthKey;
import com.expense.calculator.repository.TransactionRepository;
import com.expense.calculator.service.excel.ExcelReadSaveService;
import com.expense.calculator.utils.ExpenseCalculatorUtils;
import com.expense.calculator.utils.ObjectChecksumUtil;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.expense.calculator.utils.TransactionUtils.getTransactionType;

@Service
public class ExcelReadSaveServiceImpl implements ExcelReadSaveService {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy");
    private static final String TYPE_API = "API";

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Map<String, Integer> MONTH_ORDER = Map.ofEntries(
            Map.entry("January", 1),
            Map.entry("February", 2),
            Map.entry("March", 3),
            Map.entry("April", 4),
            Map.entry("May", 5),
            Map.entry("June", 6),
            Map.entry("July", 7),
            Map.entry("August", 8),
            Map.entry("September", 9),
            Map.entry("October", 10),
            Map.entry("November", 11),
            Map.entry("December", 12)
    );


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
        List<EpnxTransaction> transactionList = convertDataToTransactionDomain(data);
        transactionList.forEach( x -> {
            if("000000000000000".equals(x.getRefNum())){
                try {
                    x.setRefNum(ObjectChecksumUtil.generateChecksum(x));
                } catch (IOException | NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }
        });

        List<EpnxTransaction> existingTransactions = transactionRepository.findTransactionByRefNo(transactionList.stream().map(x -> x.getRefNum()).toList());
        Map<String, EpnxTransaction> existingTransactionMap = existingTransactions.stream().collect(Collectors.toMap(EpnxTransaction::getRefNum, epnxTransaction -> epnxTransaction));

        transactionList.forEach(transaction -> {
            EpnxTransaction existing = existingTransactionMap.get(transaction.getRefNum());
            if (existing != null) {
                transaction.setTransId(existing.getTransId());
            }
        });


        transactionRepository.saveAll(transactionList);
    }

    @Override
    public List<TransactionSummaryDTO> getGraphData() {
        List<Object[]> results = transactionRepository.findTransactionSummaries();
        Map<YearMonthKey, TransactionSummaryDTO> summaryMap = new HashMap<>();


        for (Object[] row : results) {
            String month = (String) row[2];
            Integer year = (Integer) row[3];
            YearMonthKey yearMonthKey = new YearMonthKey(month, year);
            if (summaryMap.get(yearMonthKey) == null) {
                TransactionSummaryDTO summaryDTO = new TransactionSummaryDTO();
                List<TransactionTypeSummary> summaryTypeList = new ArrayList<>();
                summaryDTO.setMonth(month);
                summaryDTO.setYear(year);
                summaryTypeList.add(new TransactionTypeSummary((String) row[1], (BigDecimal) row[0]));
                summaryDTO.setTransSummary(summaryTypeList);
                summaryMap.put(yearMonthKey, summaryDTO);
            } else {
                List<TransactionTypeSummary> summaryTypeList = summaryMap.get(yearMonthKey).getTransSummary();
                summaryTypeList.add(new TransactionTypeSummary((String) row[1], (BigDecimal) row[0]));
            }
        }

        List<TransactionSummaryDTO> dtos = new ArrayList<>(summaryMap.values());

        dtos.forEach(dto -> {
            BigDecimal totalSpent = dto.getTransSummary().stream()
                    .map(summary -> summary.getTotalAmount() != null ? summary.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            dto.setTotalSpent(totalSpent);
        });

        dtos = dtos.stream()
                .sorted(Comparator
                        .comparing(TransactionSummaryDTO::getYear, Comparator.reverseOrder())
                        .thenComparing(dto -> MONTH_ORDER.getOrDefault(
                                capitalize(dto.getMonth().toLowerCase()), 0), Comparator.reverseOrder()))
                .collect(Collectors.toList());

        return dtos;
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

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
