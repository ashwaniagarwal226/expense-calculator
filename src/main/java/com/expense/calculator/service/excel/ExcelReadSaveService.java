package com.expense.calculator.service.excel;

import com.expense.calculator.hdfc.DTOs.TransactionSummaryDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ExcelReadSaveService {

    void readAndSaveExcelFile(MultipartFile filePath) throws IOException;

    List<TransactionSummaryDTO> getGraphData();
}
