package com.expense.calculator.service.excel;

import java.io.IOException;

public interface ExcelReadSaveService {

    void readAndSaveExcelFile(String filePath) throws IOException;
}
