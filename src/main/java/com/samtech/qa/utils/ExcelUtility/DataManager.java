package com.samtech.qa.utils.ExcelUtility;

import com.samtech.qa.factory.DriverFactory;
import com.samtech.qa.utils.ExcelUtility.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * DataManager — Helper class for fetching test data from Excel files.
 *
 * Instead of hardcoding test data (like usernames, passwords, search terms)
 * directly in test steps, this class reads it from an Excel sheet at runtime.
 * This keeps tests flexible and easy to update — change the Excel, not the code.
 *
 * How it works:
 *   - Each row in the Excel sheet represents one test scenario's data
 *   - The "ScenarioName" column acts as the unique key to find the right row
 *   - Returns the matched row as a Map (column name → cell value)
 *
 * Usage example in a step definition:
 *   Map<String, String> data = DataManager.getTestData("LoginSheet", "ValidLogin");
 *   String username = data.get("Username");
 *   String password = data.get("Password");
 */
public class DataManager {

    /**
     * Fetches a single row of test data from an Excel sheet by scenario name.
     *
     * @param sheetName    — The name of the Excel sheet to read from (e.g. "LoginSheet")
     * @param testCaseKey  — The value in the "ScenarioName" column to match (e.g. "ValidLogin")
     * @return             — A Map where keys are column headers and values are cell contents
     * @throws RuntimeException if no row is found matching the given testCaseKey
     */
    public static Map<String, String> getTestData(String sheetName, String testCaseKey) {

        // ── Load all rows from the specified Excel sheet ──
        // ExcelReader is a Singleton — it loads the file once and reuses it,
        // avoiding repeated file I/O on every test data call.
        List<Map<String, String>> allRows = ExcelReader.getInstance().getSheetData(sheetName);

        // ── Search for the row whose "ScenarioName" matches the requested key ──
        // equalsIgnoreCase → "ValidLogin" and "validlogin" will both match
        for (Map<String, String> row : allRows) {
            if (row.get("ScenarioName").equalsIgnoreCase(testCaseKey)) {
                return row;   // Found — return this row's data to the calling test
            }
        }

        // ── No matching row found — fail fast with a clear error message ──
        // A RuntimeException is thrown so the test fails immediately with a
        // helpful message rather than continuing with null/missing data.
        throw new RuntimeException("Data Error: No row found for key '" + testCaseKey + "'");
    }
}