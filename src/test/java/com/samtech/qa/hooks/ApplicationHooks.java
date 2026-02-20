package com.samtech.qa.hooks;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.Video;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.samtech.qa.contexts.TestContext;
import com.samtech.qa.factory.DriverFactory;
import com.samtech.qa.testutilities.TestProofsCollection;
import com.samtech.qa.utils.ConfigLoader;
import com.samtech.qa.utils.FailedLocatorCollector;
import io.cucumber.java.*;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * ApplicationHooks — Cucumber lifecycle hooks that run before and after every scenario.
 *
 * Hooks are special methods that Cucumber calls automatically at key points
 * in a scenario's lifecycle — you don't call them yourself.
 *
 * This class handles everything that should happen around each scenario
 * but is NOT part of the test steps themselves:
 *   - Setting up scenario context (name, locator tracking)
 *   - Starting Playwright tracing (records every browser action)
 *   - Taking screenshots based on config flags
 *   - Saving trace files on failure (for deep debugging)
 *   - Attaching or deleting video recordings
 *   - Closing the browser cleanly after each scenario
 *   - Generating the failed locators JSON report after the full suite
 *
 * EXECUTION ORDER:
 *   @Before hooks run in ASCENDING order number (0 first, then 1)
 *   @After hooks run in DESCENDING order number (3 first, then 2, 1, 0)
 *
 *   Full sequence per scenario:
 *   ┌─────────────────────────────────────────────────┐
 *   │  @Before(0) setupScenario   → set names          │
 *   │  @Before(1) startTrace      → begin recording    │
 *   │  ... scenario steps run ...                      │
 *   │  @After(3)  captureScenarioScreenshot            │
 *   │  @After(2)  captureTrace    → save/discard trace │
 *   │  @After(1)  captureVideo    → attach/delete video│
 *   │  @After(0)  quitBrowser     → close browser      │
 *   └─────────────────────────────────────────────────┘
 *
 * WHY THIS ORDER MATTERS:
 *   Screenshots and traces must be captured BEFORE the browser closes.
 *   Video must be finalised (page closed) BEFORE attaching it to the report.
 *   Browser must close LAST so all other after-hooks can still access the page.
 */
public class ApplicationHooks {

    private TestContext testContext;    // Holds the browser, page, and scenario state
    private static final Logger logger = LoggerFactory.getLogger(ApplicationHooks.class);

    /**
     * Constructor — PicoContainer injects TestContext and TestProofsCollection automatically.
     * This is how hooks get access to the same browser session used by step definitions.
     */
    public ApplicationHooks(TestContext testContext, TestProofsCollection stepHelper) {
        this.testContext = testContext;
    }

    // ============================================================
    // SCREENSHOT HELPER
    // ============================================================

    /**
     * Takes a full-page screenshot and attaches it to the Cucumber/Allure report.
     *
     * Full-page stitching process:
     *   1. Wait for the page to fully load
     *   2. Scroll to the bottom (forces lazy-loaded content to render)
     *   3. Scroll back to the top (so the screenshot starts from the top)
     *   4. Short pause (gives the browser time to repaint after scrolling)
     *   5. Capture a full-page screenshot (Playwright stitches sections together)
     *
     * Fallback: If full-page stitching fails (e.g. on very long or dynamic pages),
     * captures just the visible area instead — better than no screenshot at all.
     *
     * @param scenario — The current Cucumber scenario (used to attach the screenshot)
     * @param prefix   — Label shown in the report (e.g. "Final Scenario Result")
     */
    private void takeScreenshot(Scenario scenario, String prefix) {
        try {
            Page page = testContext.getPage();

            // Wait for full page load before capturing
            page.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions()
                    .setTimeout(Long.parseLong(ConfigLoader.getInstance().getOptionalProp("timeout.page.load"))));

            // Scroll to bottom then back to top — ensures lazy content is rendered
            page.evaluate("() => window.scrollTo(0, document.body.scrollHeight)");
            page.evaluate("() => window.scrollTo(0, 0)");

            // Allow browser to repaint after scroll before capturing
            page.waitForTimeout(500);

            // Capture full-page screenshot (Playwright stitches multiple viewport captures)
            // Higher timeout because stitching long pages takes more time than a normal screenshot
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setTimeout(15000));

            // Attach to Cucumber report — visible in Allure under the scenario
            scenario.attach(screenshot, "image/png", prefix + " - " + scenario.getStatus());

        } catch (Exception e) {
            logger.warn("Headed capture failed: " + e.getMessage());
            // Fallback: capture only the visible viewport if full-page stitching fails
            byte[] fallback = testContext.getPage().screenshot(new Page.ScreenshotOptions().setFullPage(false));
            scenario.attach(fallback, "image/png", prefix + " (Visible Area Only)");
        }
    }

    // ============================================================
    // BEFORE HOOKS
    // ============================================================

    /**
     * @Before(order = 0) — First hook to run. Sets up scenario-level context.
     *
     * Registers the scenario name in two places:
     *   - TestContext → makes it available to step definitions if needed
     *   - FailedLocatorCollector → so any locator failures are tagged with this scenario name
     */
    @Before(order = 0)
    public void setupScenario(Scenario scenario) {
        testContext.setScenarioName(scenario.getName());
        FailedLocatorCollector.setScenarioName(scenario.getName());
        logger.info("SCENARIO STARTED: {}", scenario.getName());
    }

    /**
     * @Before(order = 1) — Runs after setupScenario. Starts Playwright tracing.
     *
     * Playwright tracing records a complete timeline of browser activity:
     *   - Every action (click, fill, navigate)
     *   - Screenshots at each step
     *   - DOM snapshots (so you can inspect the page state at any point)
     *   - Source code references
     *
     * The trace is saved as a .zip file on failure and attached to the Allure report.
     * You can open it at trace.playwright.dev for a full visual timeline — extremely
     * useful for debugging failures that are hard to reproduce locally.
     *
     * Note: getPage() here triggers lazy browser initialization if not already open.
     */
    @Before(order = 1)
    public void startTrace() {
        Page page = testContext.getPage();
        page.context().tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)   // Capture screenshots at each action
                .setSnapshots(true)     // Capture DOM snapshots for inspection
                .setSources(true));     // Include source code references in the trace
    }

    // ============================================================
    // AFTER HOOKS (run in descending order: 3 → 2 → 1 → 0)
    // ============================================================

    /**
     * @After(order = 3) — First after-hook to run. Conditionally takes a screenshot.
     *
     * Whether a screenshot is taken depends on config flags:
     *   screenshot.on.scenario.failure  → captures on FAILED scenarios
     *   screenshot.on.scenario.success  → captures on PASSED scenarios
     *   screenshot.on.scenario.skipped  → captures on SKIPPED scenarios
     *
     * All flags default to false (see ConfigLoader defaults) — enable them in
     * your {env}.config.properties file for the environments where you want them.
     *
     * Must run BEFORE captureTrace and captureVideo (browser is still open at order 3).
     */
    @After(order = 3)
    @AfterMethod(alwaysRun = true)
    public void captureScenarioScreenshot(Scenario scenario) {
        Status status = scenario.getStatus();
        boolean shouldCapture = false;

        // Check each status flag independently — only capture if the matching flag is enabled
        if (status == Status.FAILED && Boolean.parseBoolean(ConfigLoader.getInstance().getOptionalProp("screenshot.on.scenario.failure"))) {
            shouldCapture = true;
        } else if (status == Status.PASSED && Boolean.parseBoolean(ConfigLoader.getInstance().getOptionalProp("screenshot.on.scenario.success"))) {
            shouldCapture = true;
        } else if (status == Status.SKIPPED && Boolean.parseBoolean(ConfigLoader.getInstance().getOptionalProp("screenshot.on.scenario.skipped"))) {
            shouldCapture = true;
        }

        if (shouldCapture) {
            takeScreenshot(scenario, "Final Scenario Result");
            logger.debug("Screenshot for test execution captured/attached.");
        }
    }

    /**
     * @After(order = 2) — Stops Playwright tracing and handles the trace file.
     *
     * On FAILURE:
     *   - Saves the trace as a .zip file in test-output/traces/
     *   - Attaches it to the Allure report for debugging
     *   - Open the .zip at trace.playwright.dev to see a full visual replay
     *
     * On PASS:
     *   - Stops tracing without saving (trace data discarded — no overhead)
     *
     * Scenario name is sanitised (non-word characters → underscores) to create
     * a valid filename that works across all operating systems.
     */
    @After(order = 2)
    @AfterMethod(alwaysRun = true)
    public void captureTrace(Scenario scenario) {
        BrowserContext context = testContext.getDriverFactory().getTlBrowserContext().get();

        if (scenario.isFailed()) {
            // Sanitise scenario name for use as a filename (replace spaces/special chars)
            Path tracePath = Paths.get("test-output/traces/" + scenario.getName().replaceAll("\\W+", "_") + ".zip");
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));

            // Attach the trace zip to the Allure report
            try (InputStream is = Files.newInputStream(tracePath)) {
                Allure.addAttachment("Playwright Trace", "application/zip", is, ".zip");
                logger.debug("Trace record for test execution captured/attached.");
            } catch (IOException e) {
                logger.error("Failed to attach trace: " + e.getMessage());
            }
        } else {
            // Passed — stop tracing but don't save the file (saves disk space)
            context.tracing().stop(new Tracing.StopOptions().setPath(null));
        }
    }

    /**
     * @After(order = 1) — Handles video recording for the scenario.
     *
     * Video recording starts automatically when the BrowserContext is created
     * (configured in DriverFactory with setRecordVideoDir).
     * This hook finalises and handles the recorded video.
     *
     * IMPORTANT — Why page and context are closed here (not in quitBrowser):
     *   Playwright only finalises and writes the video file to disk AFTER
     *   the page and context are closed. If we waited until quitBrowser (order 0),
     *   the video file wouldn't exist yet when we try to attach it.
     *   So this hook closes page + context early to get the video, then
     *   quitBrowser (order 0) closes the remaining browser and Playwright engine.
     *
     * On FAILURE → attaches the video to the Allure report (useful for replay)
     * On PASS    → deletes the video file to save disk space
     */
    @After(order = 1)
    public void captureVideo(Scenario scenario) {
        // Get the video file path BEFORE closing the page (path becomes unavailable after close)
        Path videoPath = testContext.getPage().video().path();

        // Close page and context — this triggers Playwright to write the video file to disk
        testContext.getPage().close();
        testContext.getDriverFactory().getTlBrowserContext().get().close();

        if (scenario.isFailed()) {
            // Attach the video to Allure report for failure analysis
            try {
                Allure.addAttachment("Execution Video", "video/webm",
                        Files.newInputStream(videoPath), ".webm");
                logger.debug("Video for test execution captured/attached.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Passed — delete the video to avoid filling up disk space over many runs
            try {
                Files.deleteIfExists(videoPath);
            } catch (IOException e) {
                logger.debug("Could not delete successful test video: " + e.getMessage());
            }
        }
    }

    /**
     * @After(order = 0) — Last hook to run. Closes the browser and Playwright engine.
     *
     * Runs AFTER all other after-hooks so screenshots, traces, and videos
     * can all be captured while the browser is still alive.
     *
     * Note: Page and BrowserContext are already closed by captureVideo (order 1).
     * This call cleans up the remaining Browser and Playwright objects, and
     * removes all ThreadLocal variables to prevent memory leaks.
     */
    @After(order = 0)
    @AfterMethod(alwaysRun = true)
    public void quitBrowser(Scenario scenario) {
        logger.info("SCENARIO FINISHED: {} | Status: {}", scenario.getName(), scenario.getStatus());
        testContext.getDriverFactory().closePlaywright();
    }

    // ============================================================
    // SUITE-LEVEL TEARDOWN
    // ============================================================

    /**
     * Runs once after the entire test suite finishes (not after each scenario).
     *
     * Generates the failed locators JSON report — a summary of every selector
     * that failed during the run, grouped by locator with affected scenarios listed.
     * Written to: test-output/failedLocators_{timestamp}.json
     *
     * Does nothing if no locator failures were recorded during the run.
     */
    @AfterSuite
    @AfterMethod(alwaysRun = true)
    public void tearDownSuite() {
        FailedLocatorCollector.generateJsonReport();
    }
}