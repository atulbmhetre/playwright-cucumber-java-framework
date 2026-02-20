package com.samtech.qa.testutilities;

import com.microsoft.playwright.Page;
import com.samtech.qa.contexts.TestContext;
import com.samtech.qa.utils.ConfigLoader;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;

/**
 * TestProofsCollection — Attaches evidence (screenshots, and future artifacts) to Allure reports.
 *
 * "Test proofs" are the evidence attached to each step in the Allure report —
 * screenshots, logs, videos, etc. — that let you see exactly what the browser
 * looked like at that moment in the test.
 *
 * This class is designed to be called after each step in a step definition,
 * passing the step's outcome (PASSED/FAILED) so the right artifacts are captured
 * based on the config flags:
 *   screenshot.for.step.failed  → take screenshot when a step fails   (default: true)
 *   screenshot.for.step.passed  → take screenshot when a step passes  (default: false)
 *
 * Injected via PicoContainer into ApplicationHooks so it's available throughout
 * the scenario lifecycle alongside the TestContext.
 *
 * Designed to be easily extended — the attachStepArtifacts() method has a
 * placeholder comment for adding future artifact types (logs, page URL, etc.)
 * without changing its existing callers.
 */
public class TestProofsCollection {

    private final TestContext testContext;  // Provides access to the current browser Page
    private static final Logger logger = LoggerFactory.getLogger(TestProofsCollection.class);

    /**
     * Constructor — PicoContainer injects the shared TestContext automatically.
     *
     * @param testContext — The current scenario's context (provides access to the Page)
     */
    public TestProofsCollection(TestContext testContext) {
        this.testContext = testContext;
    }

    /**
     * Takes a screenshot of the current browser state and attaches it to the Allure report.
     *
     * Guards against two edge cases before attempting capture:
     *   - page == null  → browser was never opened (scenario failed in setup)
     *   - page.isClosed() → browser was already closed (called too late in teardown)
     *
     * The screenshot is attached inline in the Allure step — visible directly
     * in the report without needing to download anything.
     */
    private void attachScreenshot() {
        Page page = testContext.getPage();

        if (page != null && !page.isClosed()) {
            byte[] screenshot = page.screenshot();
            Allure.addAttachment(
                    "Step Screenshot",       // Label shown in the Allure report
                    "image/png",             // MIME type so Allure renders it as an image
                    new ByteArrayInputStream(screenshot),
                    ".png"
            );
        }
    }

    /**
     * Decides whether to capture and attach a screenshot based on step outcome and config flags.
     *
     * Called after each step with the step's outcome:
     *   - stepStatus.FAILED → checks "screenshot.for.step.failed" config flag
     *   - stepStatus.PASSED → checks "screenshot.for.step.passed" config flag
     *
     * Both flags default to false for PASSED and true for FAILED (see ConfigLoader defaults).
     * Enable/disable them in your {env}.config.properties file.
     *
     * Wrapped in try/catch so a screenshot failure never causes a test to fail —
     * artifact capture is always best-effort and should never mask the real test result.
     *
     * Extendable: future artifact types (page URL, console logs, network calls, etc.)
     * can be added below the screenshot block without changing how this method is called.
     *
     * @param status — The outcome of the step that just ran (PASSED or FAILED)
     */
    public void attachStepArtifacts(stepStatus status) {
        try {
            if (status == stepStatus.FAILED) {
                // Check if screenshots on step failure are enabled in config
                boolean captureFailed = Boolean.parseBoolean(
                        ConfigLoader.getInstance().getOptionalProp("screenshot.for.step.failed")
                );
                if (captureFailed) {
                    attachScreenshot();
                    logger.debug("Screenshot attached to failed step");
                }
            } else {
                // Check if screenshots on step success are enabled in config
                boolean capturePassed = Boolean.parseBoolean(
                        ConfigLoader.getInstance().getOptionalProp("screenshot.for.step.passed")
                );
                if (capturePassed) {
                    attachScreenshot();
                    logger.debug("Screenshot attached to passed step");
                }
            }

            // ── Future artifacts can be added here ──
            // e.g. attachPageUrl(), attachConsoleLogs(), attachNetworkRequests()

        } catch (Exception e) {
            // Never let artifact capture failures surface as test failures
            logger.debug("Failed to attach step artifacts: " + e.getMessage());
        }
    }
}