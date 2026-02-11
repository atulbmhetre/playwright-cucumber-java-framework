package com.samtech.qa.utils.ExcelUtility;

import com.samtech.qa.factory.DriverFactory;
import com.samtech.qa.utils.ExcelUtility.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class DataManager {
    public static Map<String, String> getTestData(String sheetName, String testCaseKey) {
        // Use the Singleton instance instead of creating a new object
        List<Map<String, String>> allRows = ExcelReader.getInstance().getSheetData(sheetName);

        for (Map<String, String> row : allRows) {
            if (row.get("ScenarioName").equalsIgnoreCase(testCaseKey)) {
                return row;
            }
        }
        throw new RuntimeException("Data Error: No row found for key '" + testCaseKey + "'");
    }
}

