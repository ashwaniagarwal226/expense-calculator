package com.expense.calculator.service.excel;

import java.io.IOException;
import java.util.List;

public interface ExcelReadService {

    List<List<String>> readExcelFile(String filePath) throws IOException;
}
