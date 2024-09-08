package com.expense.calculator.controller.hdfc;

import com.expense.calculator.hdfc.DTOs.TransactionSummaryDTO;
import com.expense.calculator.hdfc.DTOs.UploadResponse;
import com.expense.calculator.service.excel.ExcelReadSaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("v1/hdfc")
public class SaveStatementController {

    private ExcelReadSaveService excelReadSaveService;

    @Autowired
    public SaveStatementController(ExcelReadSaveService excelReadSaveService) {
        this.excelReadSaveService = excelReadSaveService;
    }

    @PostMapping("/transactionupload")
    public ResponseEntity<UploadResponse> readAndSaveStatement(@RequestParam("file") MultipartFile file) throws IOException {
        excelReadSaveService.readAndSaveExcelFile(file);
        return ResponseEntity.ok(new UploadResponse("File uploaded successfully", file.getOriginalFilename(), "success"));
    }

    @GetMapping("/graphdata")
    public ResponseEntity<List<TransactionSummaryDTO>> getMapData(){
        return ResponseEntity.ok(excelReadSaveService.getGraphData());
    }

}
