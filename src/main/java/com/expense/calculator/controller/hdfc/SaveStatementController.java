package com.expense.calculator.controller.hdfc;

import com.expense.calculator.service.excel.ExcelReadSaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("v1/hdfc")
public class SaveStatementController {

    private ExcelReadSaveService excelReadSaveService;

    @Autowired
    public SaveStatementController(ExcelReadSaveService excelReadSaveService) {
        this.excelReadSaveService = excelReadSaveService;
    }

    @GetMapping("/transaction")
    public ResponseEntity<String> readAndSaveStatement() throws IOException {
        excelReadSaveService.readAndSaveExcelFile("/Users/ashwiniagarwal/Downloads/Acct Statement_XX3928_21072024.xls");
        return ResponseEntity.ok().body("Hello");
    }

}
