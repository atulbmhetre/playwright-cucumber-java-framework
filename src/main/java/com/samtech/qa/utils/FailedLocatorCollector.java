package com.samtech.qa.utils;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samtech.qa.utils.ExcelUtility.DataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * FailedLocatorCollector — Tracks and reports selectors that failed during a test run.
 *
 * When Playwright can't find an element (broken CSS/XPath selector), this class:
 *   1. Records which selector failed and which action was being performed
 *   2. Tracks which test scenarios were affected by that failure
 *   3. At the end of the run, writes everything to a JSON report file
 *
 * This is especially useful for diagnosing flaky tests or UI changes that
 * break selectors — the JSON report tells you exactly which locators need fixing
 * and which scenarios they impacted.
 *
 * Example output in failedLocators_01012025_143022.json:
 * [
 *   {
 *     "locator": "#submit-btn",
 *     "action": "clickElement",
 *     "impacted_scenarios": ["ValidLogin", "AdminLogin"]
 *   }
 * ]
 *
 * Thread Safety:
 *   - failureLogs uses a synchronized list — safe for parallel test threads to write to
 *   - currentScenario uses ThreadLocal — each thread tracks its own scenario name
 *     independently, so parallel tests don't overwrite each other's scenario context
 */
public class FailedLocatorCollector {

    // ── Shared failure log — all threads write to this one list ──
    // Collections.synchronizedList wraps ArrayList to make it thread-safe
    // (prevents data corruption when multiple tests fail at the same time)
    private static final List<Map<String, Object>> failureLogs = Collections.synchronizedList(new ArrayList<>());

    // ── Per-thread scenario name ──
    // Each test thread stores its current scenario name here independently.
    // This lets addFailure() automatically know which scenario it's recording for,
    // without needing to pass the scenario name as a parameter every time.
    private static final ThreadLocal<String> currentScenario = new ThreadLocal<>();

    private static final Logger logger = LoggerFactory.getLogger(FailedLocatorCollector.class);

    /**
     * Sets the current scenario name for this test thread.
     * Called at the start of each scenario (in hooks) so that any locator
     * failures recorded during the scenario are linked to its name.
     *
     * @param name — The Cucumber scenario name (e.g. "User logs in with valid credentials")
     */
    public static void setScenarioName(String name) {
        currentScenario.set(name);
    }

    /**
     * Clears the scenario name for this thread after the scenario finishes.
     * Important for preventing memory leaks in long parallel test runs —
     * ThreadLocal values must be explicitly removed when no longer needed.
     */
    public static void removeScenarioName() {
        currentScenario.remove();
    }

    /**
     * Records a locator failure for the currently running scenario.
     *
     * Smart deduplication logic:
     *   - If this locator has already been logged → just adds the current scenario
     *     to its "impacted_scenarios" list (avoids duplicate locator entries)
     *   - If this locator is new → creates a fresh entry with action + scenario
     *
     * This means if "#submit-btn" fails in 5 different scenarios, the report
     * shows one entry for "#submit-btn" with all 5 scenarios listed — much
     * cleaner than 5 separate duplicate entries.
     *
     * @param action  — The method that failed (e.g. "clickElement", "enterText")
     * @param locator — The CSS/XPath selector that couldn't be found
     */
    public static void addFailure(String action, String locator) {
        String currentScenarioName = currentScenario.get();
        Map<String, Object> existingEntry = null;

        // ── Check if this locator was already recorded ──
        // Synchronized block ensures no other thread modifies the list
        // while we're searching through it
        synchronized (failureLogs) {
            for (Map<String, Object> entry : failureLogs) {
                if (entry.get("locator").equals(locator)) {
                    existingEntry = entry;
                    break;
                }
            }
        }

        if (existingEntry != null) {
            // ── Locator already exists — just add this scenario to its impact list ──
            List<String> scenarios = (List<String>) existingEntry.get("impacted_scenarios");
            if (!scenarios.contains(currentScenarioName)) {
                scenarios.add(currentScenarioName);  // Avoid listing the same scenario twice
            }
        } else {
            // ── New locator failure — create a fresh entry ──
            // LinkedHashMap preserves insertion order in the JSON output
            Map<String, Object> newEntry = new LinkedHashMap<>();
            newEntry.put("locator", locator);
            newEntry.put("action", action);

            List<String> scenarios = new ArrayList<>();
            scenarios.add(currentScenarioName);
            newEntry.put("impacted_scenarios", scenarios);

            failureLogs.add(newEntry);
            logger.debug("Locator added to failed xpath collection, locator: {}", locator);
        }
    }

    /**
     * Writes all recorded locator failures to a timestamped JSON file.
     * Called once at the end of the test run (in the TestNG teardown hook).
     *
     * Output location: test-output/failedLocators_{ddMMyyyy_HHmmss}.json
     * The timestamp in the filename makes each run's report unique and easy to find.
     *
     * Does nothing if no locator failures were recorded during the run.
     */
    public static void generateJsonReport() {
        // Skip report generation if no failures were recorded
        if (failureLogs.isEmpty()) return;

        ObjectMapper mapper = new ObjectMapper();

        // Configure pretty printing so the JSON is human-readable
        // (indented arrays with line breaks instead of one long line)
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        try {
            // Create the output directory if it doesn't already exist
            File outputDir = new File("test-output");
            if (!outputDir.exists()) outputDir.mkdirs();

            // Timestamp in filename = unique file per run, easy to sort chronologically
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyyy_HHmmss"));
            String fileName = "failedLocators_" + date + ".json";

            // Write the failure list to the JSON file with pretty formatting
            mapper.writer(printer).writeValue(new File(outputDir, fileName), failureLogs);

            logger.debug("--- Locator Report Generated: test-output/" + fileName + " ---");
        } catch (IOException e) {
            logger.error("Jackson failed to write the Failed Locator JSON report: {}", e.getMessage());
        }
    }
}