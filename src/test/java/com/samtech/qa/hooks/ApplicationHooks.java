package com.samtech.qa.hooks;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.Video;
import com.microsoft.playwright.options.LoadState;
import com.samtech.qa.contexts.TestContext;
import com.samtech.qa.factory.DriverFactory;
import com.samtech.qa.utils.ConfigLoader;
import com.samtech.qa.utils.FailedLocatorCollector;
import io.cucumber.java.*;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApplicationHooks {
    private TestContext testContext;
    private static final Logger logger = LoggerFactory.getLogger(ApplicationHooks.class);
    public ApplicationHooks(TestContext testContext) {
        this.testContext = testContext;
    }

    private void takeScreenshot(Scenario scenario, String prefix) {
        testContext.getPage().waitForLoadState(LoadState.NETWORKIDLE);
        byte[] screenshot = testContext.getPage().screenshot(new Page.ScreenshotOptions()
                .setFullPage(true)
                .setTimeout(5000));
        String attachmentName = prefix + " - " + scenario.getStatus();
        scenario.attach(screenshot, "image/png", attachmentName);
    }
    @Before(order = 0)
    public void setupScenario(Scenario scenario) {
        testContext.setScenarioName(scenario.getName());
        FailedLocatorCollector.setScenarioName(scenario.getName());
        logger.info("SCENARIO STARTED: {}",scenario.getName());
    }
    @Before(order = 1)
    public void startTrace() {
        // Start tracing before the test begins
        DriverFactory.getTlBrowserContext().get().tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true)); // Captures your source code too!
    }
    @AfterStep
    public void afterStepHook(Scenario scenario) {
        if (Boolean.parseBoolean(ConfigLoader.getInstance().getProperty("screenshot.for.step"))) {
            takeScreenshot(scenario, "Step Screenshot");
            logger.debug("Screenshot for test step captured.");
        }
    }

    @After(order = 3)
    public void captureScreenshot(Scenario scenario) {
        Status status = scenario.getStatus();
        boolean shouldCapture = false;

        if (status == Status.FAILED && Boolean.parseBoolean(ConfigLoader.getInstance().getProperty("screenshot.on.scenario.failure"))) {
            shouldCapture = true;
        } else if (status == Status.PASSED && Boolean.parseBoolean(ConfigLoader.getInstance().getProperty("screenshot.on.scenario.success"))) {
            shouldCapture = true;
        } else if (status == Status.SKIPPED && Boolean.parseBoolean(ConfigLoader.getInstance().getProperty("screenshot.on.scenario.skipped"))) {
            shouldCapture = true;
        }

        if (shouldCapture) {
            takeScreenshot(scenario, "Final Scenario Result");
            logger.debug("Screenshot for test execution captured/attached.");
        }
    }

    @After(order = 2)
    public void captureTrace(Scenario scenario) {
        BrowserContext context = DriverFactory.getTlBrowserContext().get();

        if (scenario.isFailed()) {
            Path tracePath = Paths.get("test-output/traces/" + scenario.getName().replaceAll("\\W+", "_") + ".zip");
            context.tracing().stop(new Tracing.StopOptions()
                    .setPath(tracePath));
            try (InputStream is = Files.newInputStream(tracePath)) {
                Allure.addAttachment("Playwright Trace", "application/zip", is, ".zip");
                logger.debug("Trace record for test execution captured/attached.");
            } catch (IOException e) {
                logger.error("Failed to attach trace: " + e.getMessage());
            }
        } else {
            context.tracing().stop(new Tracing.StopOptions().setPath(null));
        }
    }

    @After(order = 1)
    public void captureVideo(Scenario scenario) {
        Path videoPath = testContext.getPage().video().path();
        testContext.getPage().close();
        DriverFactory.getTlBrowserContext().get().close();
        if (scenario.isFailed()) {
            try {
                Allure.addAttachment("Execution Video", "video/webm",
                        Files.newInputStream(videoPath), ".webm");
                logger.debug("Video for test execution captured/attached.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else {
            try {
                Files.deleteIfExists(videoPath);
            } catch (IOException e) {
                logger.debug("Could not delete successful test video: " + e.getMessage());
            }
        }
    }

    @After(order = 0)
    public void quitBrowser(Scenario scenario) {
        logger.info("SCENARIO FINISHED: {} | Status: {}", scenario.getName(), scenario.getStatus());
        testContext.getDriverFactory().closePlaywright();
    }
    @AfterSuite
    public void tearDownSuite() {
        FailedLocatorCollector.generateJsonReport();
    }
}
