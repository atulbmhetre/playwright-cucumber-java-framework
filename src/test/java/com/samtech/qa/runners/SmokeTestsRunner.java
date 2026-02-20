package com.samtech.qa.runners;

import com.samtech.qa.testutilities.AllureEnvironmentManager;
import com.samtech.qa.utils.ConfigLoader;
import com.samtech.qa.utils.FailedLocatorCollector;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;

/**
 * SmokeTestsRunner — The entry point for running the smoke test suite.
 *
 * Smoke tests are a small, fast subset of tests that verify the core
 * features of the application are working. They run first on every code
 * push and act as the primary quality gate — if smoke fails, regression
 * doesn't run.
 *
 * This class tells Cucumber and TestNG:
 *   - WHICH tests to run  (features folder + @smoke tag only)
 *   - WHERE to find steps (glue packages)
 *   - HOW to run them     (parallel threads, plugins)
 *   - WHERE to write results (Allure, HTML, JSON, rerun file)
 *
 * It is triggered by TestNG via testng-smoke.xml, which is itself
 * called by Maven during the CI smoke workflow.
 *
 * KEY RESPONSIBILITIES:
 *   @BeforeSuite → initialises config and writes Allure environment info (once, before all tests)
 *   scenarios()  → sets parallel thread count and logs active run settings
 *   @AfterSuite  → generates the failed locators JSON report (once, after all tests)
 *
 * OUTPUT FILES GENERATED:
 *   target/allure-results/          → raw data for Allure HTML report
 *   target/cucumber-reports/        → standard Cucumber HTML + JSON reports
 *   target/failed_scenarios.txt     → list of failed scenarios (used by FailedTestRunner for rerun)
 *
 * DIFFERENCE FROM RegressionTestsRunner:
 *   Only the tag differs — @smoke instead of @regression.
 *   Smoke runs a smaller, faster set of scenarios focused on critical paths.
 */
@CucumberOptions(
        features = "src/test/resources/features",   // Root folder containing all .feature files
        glue = {"com.samtech.qa.stepdefinitions", "com.samtech.qa.hooks"},  // Packages with step definitions and hooks
        tags = "@smoke",                             // Only run scenarios tagged with @smoke
        plugin = {
                "pretty",                                             // Human-readable console output during the run
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",  // Generates raw data for Allure HTML report
                "html:target/cucumber-reports/cucumber.html",         // Standard Cucumber HTML report
                "json:target/cucumber-reports/cucumber.json",         // Machine-readable JSON report (useful for integrations)
                "rerun:target/failed_scenarios.txt"                   // Writes failed scenario paths — used by FailedTestRunner
        },
        dryRun = false,     // false = actually run tests; true = only validate step definitions exist (no execution)
        monochrome = true   // Strips ANSI colour codes from console output (cleaner in CI logs)
)
public class SmokeTestsRunner extends AbstractTestNGCucumberTests {

    private static final Logger logger = LoggerFactory.getLogger(SmokeTestsRunner.class);

    /**
     * Overrides the default scenarios() method to configure parallel execution
     * and log the active run settings before tests begin.
     *
     * parallel = true → TestNG runs multiple scenarios simultaneously.
     * The number of threads is controlled by "dataproviderthreadcount" in config
     * (defaults to 2 if not set — see ConfigLoader defaults).
     *
     * The diagnostic prints help confirm in CI logs that the correct environment,
     * browser, and tags were picked up from the Maven command or workflow inputs.
     */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        // Read thread count from config (or use built-in default of "2")
        String threads = ConfigLoader.getInstance().getOptionalProp("dataproviderthreadcount");
        // Set as system property so TestNG picks it up for parallel execution
        System.setProperty("dataproviderthreadcount", threads);

        // ── Diagnostic output — confirm active run settings in CI logs ──
        System.out.println("ENV FROM SYSTEM: " + System.getProperty("env"));
        System.out.println("BROWSER FROM SYSTEM: " + System.getProperty("browser"));
        System.out.println("TAGS FROM SYSTEM: " + System.getProperty("cucumber.filter.tags"));

        logger.info("Framework initialized with Threads: {}",
                System.getProperty("dataproviderthreadcount"));

        // Hand off to Cucumber's default logic to load and return all matching scenarios
        return super.scenarios();
    }

    /**
     * Runs ONCE before any test in the suite starts.
     *
     * Two responsibilities:
     *   1. Ensure ConfigLoader is initialised — forces config files to be loaded
     *      upfront so any missing config errors surface immediately, not mid-run
     *   2. Write Allure environment info — records browser, env, and other settings
     *      into the Allure report so you can see the exact conditions of the run
     */
    @BeforeSuite(alwaysRun = true)
    public void setupSuite() {
        ConfigLoader.getInstance();                      // Trigger eager config load
        AllureEnvironmentManager.writeEnvironmentInfo(); // Write env details to Allure report
    }

    /**
     * Runs ONCE after all tests in the suite have finished.
     *
     * Generates the failed locators JSON report — a summary of every selector
     * that failed during the run, with the affected scenarios listed per locator.
     * Written to: test-output/failedLocators_{timestamp}.json
     *
     * NOTE: This is the authoritative @AfterSuite for the smoke suite.
     * TestNG only recognises @AfterSuite on the runner class, not on hook classes —
     * that's why this lives here rather than in ApplicationHooks.
     */
    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        logger.debug("--- All tests finished. Generating Failed Locator Report ---");
        FailedLocatorCollector.generateJsonReport();
    }
}