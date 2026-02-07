package com.samtech.qa.utils.ExcelUtility;

import com.samtech.qa.utils.ElementUtils;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class ExcelReader {

    private static final Logger logger = LoggerFactory.getLogger(ExcelReader.class);

    public List<Map<String, String>> getSheetData(String sheetName) {
        String env = System.getProperty("env", "QA");
        String filePath = System.getProperty("user.dir") + "/src/test/resources/testdata/" + env.trim().toLowerCase() + "/TestData.xlsx";
        logger.debug("Test Data file path: {}", filePath);

        List<Map<String, String>> dataList = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(new File(filePath))) {
            Sheet sheet = workbook.getSheet(sheetName);
            Row headerRow = sheet.getRow(0);

            // PROACTIVE: Helper to handle formulas and formatting automatically
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row currentRow = sheet.getRow(i);
                if (currentRow == null) continue;

                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int j = 0; j < headerRow.getLastCellNum(); j++) {
                    String columnName = headerRow.getCell(j).getStringCellValue();
                    Cell cell = currentRow.getCell(j);

                    // PROACTIVE: This line solves the "Type Irritation"
                    // It formats the cell value as a String exactly as it appears in Excel
                    String cellValue = formatter.formatCellValue(cell, evaluator);

                    rowMap.put(columnName, cellValue);
                }
                dataList.add(rowMap);
            }
            logger.debug("Every row of Test Data is read from test data file from sheet: {}", sheetName);
        } catch (Exception e) {
            logger.debug("Excel error: "+ e.getMessage());
            throw new RuntimeException("Excel Error: " + e.getMessage());
        }
        return dataList;
    }
}
