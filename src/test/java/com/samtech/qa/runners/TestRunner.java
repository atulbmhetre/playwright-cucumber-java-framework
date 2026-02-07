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
        tags = "@Login",
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
    @DataProvider(parallel = true) // This MUST be set to true
    public Object[][] scenarios() {
        String threads = System.getProperty("dataproviderthreadcount",
                ConfigLoader.getInstance().getProperty("dataproviderthreadcount", "1"));
        System.setProperty("dataproviderthreadcount", threads);
        logger.debug("Parallel DataProvider thread count set to: " + threads);
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
