package com.samtech.qa.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;

import java.io.File;

/**
 * FailedTestRunner — Re-runs only the scenarios that failed in the previous test run.
 *
 * When tests fail, the main runner (SmokeRunner / RegressionRunner) writes the
 * failed scenario locations to: target/failed_scenarios.txt
 * This runner reads that file and re-executes only those failed scenarios.
 *
 * WHY RERUN FAILED TESTS?
 *   Some test failures are caused by environmental flakiness (network blips,
 *   slow CI machines, timing issues) rather than real bugs. Re-running gives
 *   those tests a second chance before marking the build as failed —
 *   reducing false alarms and noise in the test results.
 *
 * HOW IT WORKS:
 *   1. Main runner runs all tests → writes failed scenario paths to failed_scenarios.txt
 *   2. FailedTestRunner reads that file and runs only those scenarios
 *   3. If the file is empty or missing (all tests passed), this runner skips itself
 *
 * SAFETY GUARDS:
 *   Two checks prevent this runner from crashing when there's nothing to rerun:
 *     - @BeforeClass → throws SkipException to skip the class gracefully
 *     - scenarios()  → returns an empty array as a secondary safety net
 *
 * The "isRerun=true" system property is set so other parts of the framework
 * can detect they're in a rerun context if needed (e.g. to adjust reporting).
 */
@CucumberOptions(
        features = "@target/failed_scenarios.txt",  // "@" prefix = read scenario paths from a file
        glue = {"com.samtech.qa.stepdefinitions", "com.samtech.qa.hooks"},  // Where to find step definitions and hooks
        plugin = {
                "pretty",                                           // Human-readable console output
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm" // Generates Allure report data
        },
        monochrome = true   // Strips ANSI colour codes from console output (cleaner in CI logs)
)
public class FailedTestRunner extends AbstractTestNGCucumberTests {

    // ── Set system property at class load time ──
    // static block runs once when the class is first loaded by the JVM —
    // before any constructor or @Before method.
    // This flags the framework as being in "rerun mode" for the entire run.
    static {
        System.setProperty("isRerun", "true");
    }

    /**
     * Runs once before any scenarios in this class are executed.
     * If there are no failed scenarios to rerun, skips the entire class gracefully.
     *
     * SkipException is TestNG's way of saying "nothing to do here — skip cleanly"
     * without causing an error or false failure in the build.
     */
    @BeforeClass
    public void checkRerunFile() {
        File rerunFile = new File("target/failed_scenarios.txt");
        if (!rerunFile.exists() || rerunFile.length() == 0) {
            throw new SkipException("No failed scenarios to rerun — skipping FailedTestRunner");
        }
    }

    /**
     * Provides the list of scenarios to run — overridden here to add a safety check.
     *
     * parallel = true → re-runs failed scenarios in parallel (same as the main runner)
     *
     * Why override this?
     *   AbstractTestNGCucumberTests.scenarios() would crash if the rerun file
     *   doesn't exist. This override adds a guard that returns an empty array
     *   instead — a secondary safety net on top of the @BeforeClass check.
     *
     * @return — Array of scenario data to run, or empty array if nothing to rerun
     */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        File rerunFile = new File("target/failed_scenarios.txt");
        if (!rerunFile.exists() || rerunFile.length() == 0) {
            return new Object[0][0];  // Empty = nothing to run, no crash
        }
        return super.scenarios();  // File has content — hand off to default Cucumber behaviour
    }
}