package com.samtech.qa.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElementUtils {

    private static final Logger logger = LoggerFactory.getLogger(ElementUtils.class);
    private Page page;

    public ElementUtils(Page page) {
        this.page = page;
    }

    private double getTimeout() {
        String timeout = ConfigLoader.getInstance().getProperty("timeout.global.wait","15000");
        return Double.parseDouble(timeout);
    }

    public void clickElement(String... selectors) {
        boolean success = false;
        double fallbackWait = getTimeout() / 3;

        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector);
                locator.waitFor(new Locator.WaitForOptions().setTimeout(fallbackWait));
                locator.click();
                logger.debug("clicked element with selector: {}", selector);
                success = true;
                break;
            } catch (Exception e) {
                // Pulls scenario name automatically via ThreadLocal in collector
                logger.debug("Failed to click element with selector: {}", selector);
                FailedLocatorCollector.addFailure("clickElement", selector);
            }
        }
        if (!success) throw new RuntimeException("All locators failed for clickElement.");
    }

    public void enterText(String value, String... selectors) {
        boolean success = false;
        // For fallback, we use 1/3 of the global timeout per attempt
        double fallbackTimeout = getTimeout() / 3;

        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector);
                locator.waitFor(new Locator.WaitForOptions().setTimeout(fallbackTimeout));
                locator.fill(value);
                logger.debug("Entered text successfully using selector: {}, text: {}", selector, value);
                success = true;
                break;
            } catch (Exception e) {
                logger.debug("Failed to Enter text using selector: {}", selector);
                FailedLocatorCollector.addFailure("enterText", selector);
            }
        }
        if (!success) throw new RuntimeException("Action Failed: None of the provided locators for 'enterText' were found.");
    }

    public String getElementText(String... selectors) {
        double fallbackTimeout = getTimeout() / 3;
        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector);
                locator.waitFor(new Locator.WaitForOptions().setTimeout(fallbackTimeout));
                String text = locator.textContent().trim();
                logger.debug("Text retrieved from selector: {} is {}", selector, text);
                return text;
            } catch (Exception e) {
                logger.debug("Failed to fetch element text from selector: {}", selector);
                FailedLocatorCollector.addFailure("getElementText", selector);
            }
        }
        throw new RuntimeException("Action Failed: None of the provided locators for 'getElementText' were found.");
    }

    public boolean isElementVisible(String... selectors) {
        for (String selector : selectors) {
            try {
                // This is the key: Force Playwright to wait up to 5s for each locator
                page.locator(selector).waitFor(new Locator.WaitForOptions().setTimeout(5000));
                return true;
            } catch (Exception e) {
                // Log to JSON and try the next fallback
                logger.debug("Selector is not visible, Selector: {}", selector);
                FailedLocatorCollector.addFailure("visibilityCheck", selector);
            }
        }
        return false;
    }

    public void waitForPageURL(String urlPart) {
        double timeout = getTimeout();
        long startTime = System.currentTimeMillis();
        boolean found = false;

        // Simple loop: Keep checking the URL until it matches or time runs out
        while (System.currentTimeMillis() - startTime < timeout) {
            if (page.url().toLowerCase().contains(urlPart.toLowerCase())) {
                found = true;
                logger.debug("Found \"{}\" in current URL", urlPart);
                break;
            }
            // Small pause to prevent "hammering" the CPU
            page.waitForTimeout(500);
        }

        if (!found) {
            throw new RuntimeException("Timed out waiting for URL to contain: " + urlPart
                    + ". Current URL is: " + page.url());
        }
    }

    public void waitForPageLoad() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions()
                .setTimeout(Long.parseLong(ConfigLoader.getInstance().getProperty("timeout.page.load"))));
    }

    public String getPageTitle() {
        String title = page.title();
        logger.debug("Current Page title: {}", title);
        return title;
    }

    public Page getPage() {
        return page;
    }

}
