package com.samtech.qa.utils.ExcelUtility;

import com.samtech.qa.factory.DriverFactory;
import com.samtech.qa.utils.ExcelUtility.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class DataManager {

    private static final Logger logger = LoggerFactory.getLogger(DataManager.class);

    public static Map<String, String> getTestData(String sheetName, String testCaseKey) {
        ExcelReader reader = new ExcelReader();
        List<Map<String, String>> allRows = reader.getSheetData(sheetName);
        if (testCaseKey == null) {
            throw new RuntimeException("Data Error: Scenario name ("+testCaseKey+") passed to DataManager is null.");
        }
        for (Map<String, String> row : allRows) {
            if (row.get("ScenarioName").equalsIgnoreCase(testCaseKey)) {
                logger.debug("{} row found in excel sheet.", testCaseKey);
                return row;
            }
        }
        logger.debug("Data Error: Could not find a row with TestCaseKey '"
                + testCaseKey + "' in sheet '" + sheetName + "'");
        throw new RuntimeException("Data Error: Could not find a row with TestCaseKey '"
                + testCaseKey + "' in sheet '" + sheetName + "'");
    }
}
