package com.samtech.qa.utils.ExcelUtility;

import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.util.*;

public class ExcelReader {
    private static ExcelReader instance;
    // The Cache: Stores data as {SheetName -> List of Rows}
    private static final Map<String, List<Map<String, String>>> dataCache = new HashMap<>();

    // Private constructor prevents 'new ExcelReader()' from other classes
    private ExcelReader() {}

    public static synchronized ExcelReader getInstance() {
        if (instance == null) {
            instance = new ExcelReader();
        }
        return instance;
    }

    public List<Map<String, String>> getSheetData(String sheetName) {
        // PROACTIVE: If we already read this sheet, return it from memory
        if (dataCache.containsKey(sheetName)) {
            return dataCache.get(sheetName);
        }

        String env = System.getProperty("env", "QA");
        String filePath = System.getProperty("user.dir") + "/src/test/resources/testdata/" + env.trim().toLowerCase() + "/TestData.xlsx";
        List<Map<String, String>> dataList = new ArrayList<>();

        // BufferedInputStream for better thread-safe file handling
        try (InputStream is = new BufferedInputStream(new FileInputStream(filePath));
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheet(sheetName);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row currentRow = sheet.getRow(i);
                if (currentRow == null) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int j = 0; j < headerRow.getLastCellNum(); j++) {
                    String columnName = headerRow.getCell(j).getStringCellValue();
                    rowMap.put(columnName, formatter.formatCellValue(currentRow.getCell(j)));
                }
                dataList.add(rowMap);
            }
            // Store in cache for future scenarios
            dataCache.put(sheetName, dataList);

        } catch (Exception e) {
            throw new RuntimeException("Excel Error: " + e.getMessage());
        }
        return dataList;
    }
}
