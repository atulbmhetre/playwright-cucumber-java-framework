package com.samtech.qa.runners;

import com.samtech.qa.testutilities.AllureEnvironmentManager;
import com.samtech.qa.utils.ConfigLoader;
import com.samtech.qa.utils.FailedLocatorCollector;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;

@CucumberOptions(features = "src/test/resources/features",
        glue = {"com.samtech.qa.stepdefinitions", "com.samtech.qa.hooks"},
        tags = "@abcd",
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
        String threads = ConfigLoader.getInstance().getOptionalProp("dataproviderthreadcount");
        System.setProperty("dataproviderthreadcount", threads);

        logger.info("Framework initialized with Threads: {}",
                System.getProperty("dataproviderthreadcount"));

        return super.scenarios();
    }

    @BeforeSuite(alwaysRun = true)
    public void setupSuite() {
        ConfigLoader.getInstance(); // ensure initialized
        AllureEnvironmentManager.writeEnvironmentInfo();
    }

    // This is the ONLY place TestNG will look for @AfterSuite
    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        logger.debug("--- All tests finished. Generating Failed Locator Report ---");
        FailedLocatorCollector.generateJsonReport();
    }
}
