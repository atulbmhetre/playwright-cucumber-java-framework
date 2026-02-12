package com.samtech.qa.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;

import java.io.File;

@CucumberOptions(
        features = "@target/failed_scenarios.txt",
        glue = {"com.samtech.qa.stepdefinitions", "com.samtech.qa.hooks"},
        plugin = {
                "pretty",
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"
        },
        monochrome = true
)
public class FailedTestRunner extends AbstractTestNGCucumberTests {

    static {
        System.setProperty("isRerun", "true");
    }
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        File rerunFile = new File("target/failed_scenarios.txt");
        if (!rerunFile.exists() || rerunFile.length() == 0) {
            return new Object[0][0]; // Returns nothing to run if file is missing/empty
        }
        return super.scenarios();
    }
}
