package com.samtech.qa.utils.ExcelUtility;

import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.util.*;

/**
 * ExcelReader — Reads test data from Excel files and caches it in memory.
 *
 * This class uses two important design patterns:
 *
 *   1. SINGLETON — Only one ExcelReader instance exists for the entire test run.
 *      This avoids repeatedly opening and closing the Excel file for every test,
 *      which would be slow and wasteful.
 *
 *   2. CACHE — Once a sheet is read, its data is stored in memory (the cache).
 *      Any future request for the same sheet is served from memory instantly,
 *      without touching the file again.
 *
 * How the data is structured after reading:
 *   Excel Sheet → List of rows → Each row is a Map of { "ColumnHeader" : "CellValue" }
 *
 *   Example:
 *     Sheet "LoginSheet" row 2 → { "ScenarioName": "ValidLogin", "Username": "admin", "Password": "pass123" }
 *
 * File location is determined at runtime based on the "env" system property:
 *   src/test/resources/testdata/{env}/TestData.xlsx
 *   e.g. for QA: src/test/resources/testdata/qa/TestData.xlsx
 */
public class ExcelReader {

    // The single shared instance of this class (Singleton pattern)
    private static ExcelReader instance;

    // ── In-memory cache ──────────────────────────────────────────────────────
    // Stores already-read sheets so we never read the same sheet twice.
    // Structure: { "SheetName" → [ {col: val, col: val}, {col: val, col: val}, ... ] }
    private static final Map<String, List<Map<String, String>>> dataCache = new HashMap<>();

    // ── Private constructor ──────────────────────────────────────────────────
    // Prevents other classes from doing 'new ExcelReader()'.
    // Forces all access through getInstance() to guarantee only one instance exists.
    private ExcelReader() {}

    /**
     * Returns the single shared ExcelReader instance.
     * Creates it on the first call; returns the existing one on all subsequent calls.
     *
     * "synchronized" ensures thread safety — if two test threads call this
     * at the exact same time, only one instance gets created (not two).
     */
    public static synchronized ExcelReader getInstance() {
        if (instance == null) {
            instance = new ExcelReader();
        }
        return instance;
    }

    /**
     * Returns all rows from the specified Excel sheet as a list of maps.
     * If the sheet was already read before, returns cached data immediately.
     *
     * @param sheetName — Name of the Excel sheet to read (must match exactly, e.g. "LoginSheet")
     * @return          — List of rows, each row being a Map of { column header → cell value }
     * @throws RuntimeException if the file is not found or the sheet cannot be read
     */
    public List<Map<String, String>> getSheetData(String sheetName) {

        // ── Cache hit — return data from memory without touching the file ──
        // This is the most common path after the first read of a sheet.
        if (dataCache.containsKey(sheetName)) {
            return dataCache.get(sheetName);
        }

        // ── Determine the Excel file path based on current test environment ──
        // "env" is passed as a Maven flag (-Denv=qa) or defaults to "QA"
        String env = System.getProperty("env", "QA");
        String filePath = System.getProperty("user.dir") + "/src/test/resources/testdata/" + env.trim().toLowerCase() + "/TestData.xlsx";

        //Validate if file exist for current environment, if not present do fastfail with a clear message
        File testData = new File(filePath);
        if (!testData.exists()) {
            throw new RuntimeException(
                    "TestData.xlsx not found for environment: " + env
                            + ". Expected at: " + testData.getPath());
        }

        List<Map<String, String>> dataList = new ArrayList<>();

        // ── Open and read the Excel file ──
        // BufferedInputStream wraps FileInputStream for better performance
        // and more reliable behaviour when reading large files.
        // The try-with-resources block ensures the file is always closed
        // after reading, even if an error occurs midway.
        try (InputStream is = new BufferedInputStream(new FileInputStream(filePath));
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheet(sheetName);

            // DataFormatter converts any cell type (number, date, boolean, etc.)
            // to a plain String — so all values are handled consistently
            DataFormatter formatter = new DataFormatter();

            // Row 0 is always the header row (column names like "ScenarioName", "Username", etc.)
            Row headerRow = sheet.getRow(0);

            // ── Read each data row (starting from row 1, skipping the header) ──
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row currentRow = sheet.getRow(i);
                if (currentRow == null) continue;   // Skip completely empty rows

                // LinkedHashMap preserves column order as they appear in the Excel file
                Map<String, String> rowMap = new LinkedHashMap<>();

                // Map each cell to its column header name
                for (int j = 0; j < headerRow.getLastCellNum(); j++) {
                    String columnName = headerRow.getCell(j).getStringCellValue();
                    rowMap.put(columnName, formatter.formatCellValue(currentRow.getCell(j)));
                }
                dataList.add(rowMap);
            }

            // ── Store the result in cache so this sheet is never read from disk again ──
            dataCache.put(sheetName, dataList);

        } catch (Exception e) {
            // Wrap any file/read error in a RuntimeException with a clear message
            throw new RuntimeException("Excel Error: " + e.getMessage());
        }

        return dataList;
    }
}