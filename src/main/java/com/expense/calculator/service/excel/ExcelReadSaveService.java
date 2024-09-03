package com.expense.calculator.service.excel;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ExcelReadSaveService {

    void readAndSaveExcelFile(MultipartFile filePath) throws IOException;
}
