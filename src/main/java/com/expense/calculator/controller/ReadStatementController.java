package com.expense.calculator.controller;

import com.expense.calculator.service.excel.ExcelReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("v1/read")
public class ReadStatementController {

    private ExcelReadService excelReadService;

    @Autowired
    public ReadStatementController(ExcelReadService excelReadService) {
        this.excelReadService = excelReadService;
    }

    @GetMapping("/greeting")
    public ResponseEntity<String> greeting() throws IOException {
        excelReadService.readExcelFile("/Users/ashwiniagarwal/Downloads/Acct Statement_XX3928_21072024.xls");
        return ResponseEntity.ok().body("Hello");
    }

}
