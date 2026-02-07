package com.samtech.qa.testutilities;

import com.samtech.qa.utils.ConfigLoader;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

public class RetryAnalyzer implements IRetryAnalyzer {
    private int count = 0;

    @Override
    public boolean retry(ITestResult result) {
        // Pulls from Maven -Dretry=1 or Config. If neither, defaults to 0 (no retry)
        int maxRetryLimit = Integer.parseInt(
                System.getProperty("retry", ConfigLoader.getInstance().getProperty("retry", "0")));

        if (!result.isSuccess() && count < maxRetryLimit) {
            count++;
            return true; // TestNG will immediately rerun the scenario
        }
        return false;
    }
}
