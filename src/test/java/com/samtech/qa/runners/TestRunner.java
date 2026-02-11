package com.samtech.qa.runners;

import com.samtech.qa.testutilities.RetryAnalyzer;
import com.samtech.qa.utils.ConfigLoader;
import com.samtech.qa.utils.ElementUtils;
import com.samtech.qa.utils.FailedLocatorCollector;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import io.cucumber.testng.FeatureWrapper;
import io.cucumber.testng.PickleWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

@CucumberOptions(features = "src/test/resources/features",
        glue = {"com.samtech.qa.stepdefinitions", "com.samtech.qa.hooks"},
        tags = "@smoke",
        plugin = {
                "pretty",
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",
                "html:target/cucumber-reports/cucumber.html",
                "json:target/cucumber-reports/cucumber.json",
                "rerun:target/failed_scenarios.txt"
        },
        dryRun = false,
        monochrome = true)
public class TestRunner extends AbstractTestNGCucumberTests {

    private static final Logger logger = LoggerFactory.getLogger(TestRunner.class);

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        // 1. Read from config.properties
        String threads = ConfigLoader.getInstance().getProperty("dataproviderthreadcount");
        String retry = ConfigLoader.getInstance().getProperty("retry.count");

        // 2. Set as System Properties so Cucumber and TestNG can "see" them
        // If config returns null, we default to "1"
        System.setProperty("dataproviderthreadcount", (threads != null) ? threads : "1");
        System.setProperty("cucumber.testng.retry.count", (retry != null) ? retry : "1");

        logger.info("Framework initialized with Threads: {} and Retry Count: {}",
                System.getProperty("dataproviderthreadcount"),
                System.getProperty("cucumber.testng.retry.count"));

        return super.scenarios();
    }


    @Override
    @Test(groups = "cucumber", description = "Runs Cucumber Scenarios", dataProvider = "scenarios")
    public void runScenario(io.cucumber.testng.PickleWrapper pickleWrapper, io.cucumber.testng.FeatureWrapper featureWrapper) {
        super.runScenario(pickleWrapper, featureWrapper);
    }

    // This is the ONLY place TestNG will look for @AfterSuite
    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        logger.debug("--- All tests finished. Generating Failed Locator Report ---");
        FailedLocatorCollector.generateJsonReport();
    }
}
