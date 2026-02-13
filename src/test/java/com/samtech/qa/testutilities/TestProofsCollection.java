package com.samtech.qa.testutilities;

import com.microsoft.playwright.Page;
import com.samtech.qa.contexts.TestContext;
import com.samtech.qa.runners.TestRunner;
import com.samtech.qa.utils.ConfigLoader;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;

public class TestProofsCollection {

    private final TestContext testContext;
    private static final Logger logger = LoggerFactory.getLogger(TestProofsCollection.class);

    public TestProofsCollection(TestContext testContext){
        this.testContext = testContext;
    }

    private void attachScreenshot() {

        Page page = testContext.getPage();

        if (page != null && !page.isClosed()) {
            byte[] screenshot = page.screenshot();
            Allure.addAttachment(
                    "Step Screenshot",
                    "image/png",
                    new ByteArrayInputStream(screenshot),
                    ".png"
            );
        }
    }

    public void attachStepArtifacts(stepStatus status) {
        try {
            // Decide whether to attach based on step result
            if (status == stepStatus.FAILED) {
                boolean captureFailed = Boolean.parseBoolean(
                        ConfigLoader.getInstance().getOptionalProp("screenshot.for.step.failed")
                );
                if (captureFailed) {
                    attachScreenshot();
                    logger.debug("Screenshot attached to failed step");
                }
            } else {
                boolean capturePassed = Boolean.parseBoolean(
                        ConfigLoader.getInstance().getOptionalProp("screenshot.for.step.passed")
                );
                if (capturePassed) {
                    attachScreenshot();
                    logger.debug("Screenshot attached to passed step");
                }
            }

            // Future artifacts (logs, URL, etc.) can go here
            // e.g., attachLogs(), attachPageUrl(), etc.

        } catch (Exception e) {
            logger.debug("Failed to attach step artifacts: " + e.getMessage());
        }
    }



}
