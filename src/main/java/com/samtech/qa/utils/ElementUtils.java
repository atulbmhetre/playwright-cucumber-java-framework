package com.samtech.qa.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ElementUtils — Reusable helper methods for interacting with web page elements.
 *
 * This class wraps Playwright's raw API into clean, test-friendly methods that:
 *   - Accept multiple fallback selectors (if the first fails, tries the next)
 *   - Wait for elements to be ready before acting on them
 *   - Log failures and track broken selectors via FailedLocatorCollector
 *   - Throw clear errors if all selectors fail, so tests fail with useful messages
 *
 * FALLBACK SELECTOR PATTERN:
 *   Most methods accept varargs (String... selectors), meaning you can pass
 *   multiple CSS/XPath selectors for the same element. This is useful when
 *   the UI has inconsistent markup across environments or releases.
 *
 *   Example:
 *     clickElement("#submit-btn", "button[type='submit']", "//button[text()='Submit']");
 *   → Tries each selector in order. Uses the first one that works.
 *
 * TIMEOUT STRATEGY:
 *   The global timeout (timeout.global.wait) is used for full waits.
 *   For fallback attempts, 1/3 of that timeout is used per selector —
 *   so the total wait across 3 selectors equals one full global timeout.
 */
public class ElementUtils {

    private static final Logger logger = LoggerFactory.getLogger(ElementUtils.class);

    // The Playwright Page object — represents the active browser tab
    private Page page;

    /**
     * Constructor — requires a Page so ElementUtils always operates
     * on the correct browser tab for the current test thread.
     */
    public ElementUtils(Page page) {
        this.page = page;
    }

    /**
     * Reads the global wait timeout from config.
     * Centralised here so all methods stay in sync with one config value.
     *
     * @return timeout in milliseconds as a double (Playwright expects double)
     */
    private double getTimeout() {
        String timeout = ConfigLoader.getInstance().getOptionalProp("timeout.global.wait");
        return Double.parseDouble(timeout);
    }

    // ============================================================
    // CLICK
    // ============================================================

    /**
     * Clicks an element identified by one or more selectors.
     *
     * For each selector:
     *   - Waits up to 1/3 of the global timeout for the element to appear
     *   - Clicks it if found, then waits for the page to finish loading
     *   - Moves to the next selector if this one fails
     *
     * Throws RuntimeException if all selectors fail.
     *
     * @param selectors — One or more CSS/XPath selectors to try (in order)
     */
    public void clickElement(String... selectors) {
        boolean success = false;
        // Divide the global timeout equally across fallback attempts
        double fallbackWait = getTimeout() / 3;

        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector);
                locator.waitFor(new Locator.WaitForOptions().setTimeout(fallbackWait));
                locator.click();
                waitForPageLoad();  // Wait for any navigation triggered by the click
                logger.debug("clicked element with selector: {}", selector);
                success = true;
                break;  // Stop trying further selectors — this one worked
            } catch (Exception e) {
                logger.debug("Failed to click element with selector: {}", selector);
                // Record this failed selector for post-run analysis
                FailedLocatorCollector.addFailure("clickElement", selector);
            }
        }
        if (!success) throw new RuntimeException("All locators failed for clickElement.");
    }

    // ============================================================
    // TYPE / FILL TEXT
    // ============================================================

    /**
     * Types text into an input field identified by one or more selectors.
     *
     * Uses Playwright's fill() which clears any existing value before typing —
     * more reliable than sendKeys-style typing for most input fields.
     *
     * Throws RuntimeException if all selectors fail.
     *
     * @param value     — The text to type into the field
     * @param selectors — One or more CSS/XPath selectors to try (in order)
     */
    public void enterText(String value, String... selectors) {
        boolean success = false;
        // Use 1/3 of global timeout per fallback attempt
        double fallbackTimeout = getTimeout() / 3;

        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector);
                locator.waitFor(new Locator.WaitForOptions().setTimeout(fallbackTimeout));
                locator.fill(value);  // Clears field first, then types the value
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

    // ============================================================
    // GET TEXT
    // ============================================================

    /**
     * Retrieves the visible text content of an element.
     *
     * Waits for the page to load first, then tries each selector in order.
     * Returns the trimmed text of the first element found.
     *
     * Throws RuntimeException if all selectors fail.
     *
     * @param selectors — One or more CSS/XPath selectors to try (in order)
     * @return          — The trimmed text content of the matched element
     */
    public String getElementText(String... selectors) {
        waitForPageLoad();  // Ensure page is ready before trying to read text
        double fallbackTimeout = getTimeout() / 3;

        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector);
                locator.waitFor(new Locator.WaitForOptions().setTimeout(fallbackTimeout));
                String text = locator.textContent().trim();  // .trim() removes whitespace from edges
                logger.debug("Text retrieved from selector: {} is {}", selector, text);
                return text;
            } catch (Exception e) {
                logger.debug("Failed to fetch element text from selector: {}", selector);
                FailedLocatorCollector.addFailure("getElementText", selector);
            }
        }
        throw new RuntimeException("Action Failed: None of the provided locators for 'getElementText' were found.");
    }

    // ============================================================
    // VISIBILITY CHECK
    // ============================================================

    /**
     * Checks whether an element is visible on the page.
     *
     * Tries each selector in order. Returns true as soon as one is found visible.
     * Returns false (without throwing) if none are visible — useful for conditional
     * logic in tests (e.g. "if element is visible, then click it").
     *
     * @param selectors — One or more CSS/XPath selectors to try (in order)
     * @return          — true if any selector finds a visible element, false otherwise
     */
    public boolean isElementVisible(String... selectors) {
        for (String selector : selectors) {
            try {
                waitForPageLoad();
                // Wait up to the full global timeout for this element to become visible
                page.locator(selector).waitFor(new Locator.WaitForOptions()
                        .setTimeout(Long.parseLong(ConfigLoader.getInstance().getOptionalProp("timeout.global.wait"))));
                return true;  // Element found and visible
            } catch (Exception e) {
                logger.debug("Selector is not visible, Selector: {}", selector);
                FailedLocatorCollector.addFailure("visibilityCheck", selector);
            }
        }
        return false;  // None of the selectors found a visible element
    }

    // ============================================================
    // URL WAIT
    // ============================================================

    /**
     * Waits until the current page URL contains the expected partial string.
     *
     * Useful after clicks or form submissions that trigger a page navigation —
     * confirms the browser has landed on the right page before tests continue.
     *
     * Uses a polling loop (checks every 500ms) rather than a single wait,
     * which is more reliable for SPAs (Single Page Applications) where
     * URL changes may happen gradually.
     *
     * Throws RuntimeException with the actual URL if the timeout expires.
     *
     * @param urlPart — A substring expected to appear in the URL (case-insensitive)
     */
    public void waitForPageURL(String urlPart) {
        double timeout = getTimeout();
        long startTime = System.currentTimeMillis();
        boolean found = false;

        // Poll the URL every 500ms until it matches or time runs out
        while (System.currentTimeMillis() - startTime < timeout) {
            if (page.url().toLowerCase().contains(urlPart.toLowerCase())) {
                found = true;
                logger.debug("Found \"{}\" in current URL", urlPart);
                break;
            }
            // Small pause between checks — prevents hammering the CPU in a tight loop
            page.waitForTimeout(500);
        }

        if (!found) {
            throw new RuntimeException("Timed out waiting for URL to contain: " + urlPart
                    + ". Current URL is: " + page.url());
        }
    }

    // ============================================================
    // PAGE LOAD WAITS
    // ============================================================

    /**
     * Waits for the page's DOM to finish loading (DOMContentLoaded event).
     *
     * This is a lighter wait than waitForPageStable() — it confirms the HTML
     * structure is ready but doesn't wait for images, fonts, or network calls.
     * Used internally after clicks that may trigger a navigation.
     */
    public void waitForPageLoad() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions()
                .setTimeout(Long.parseLong(ConfigLoader.getInstance().getOptionalProp("timeout.page.load"))));
    }

    /**
     * Waits for the page to be fully stable — DOM loaded, network idle, and rendered.
     *
     * This is a heavier, three-stage wait designed for pages that:
     *   - Have async API calls that complete after initial load
     *   - Are Single Page Applications (React, Angular, Vue) with client-side rendering
     *   - Show loading spinners or skeleton screens before content appears
     *
     * Stages:
     *   1. Wait for <body> to be visible (confirms basic DOM is ready)
     *   2. Wait for network to go idle (no more API calls in flight)
     *      → Wrapped in try/catch: some pages never fully go idle, which is fine
     *   3. Short 200ms buffer → allows the browser's render/paint cycle to finish
     *      before test steps try to interact with freshly rendered elements
     */
    public void waitForPageStable() {
        try {
            // Stage 1: Confirm the page body is visible (DOM is attached and rendered)
            page.waitForSelector("body",
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(Long.parseLong(ConfigLoader.getInstance().getOptionalProp("timeout.global.wait"))));

            // Stage 2: Wait for network activity to settle (no pending XHR/fetch calls)
            // "best effort" — if the page keeps making background calls, we move on anyway
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions()
                            .setTimeout(Long.parseLong(ConfigLoader.getInstance().getOptionalProp("timeout.page.load"))));

        } catch (Exception e) {
            // Network idle not reached — acceptable for apps with polling/websockets
            System.out.println("Network idle not reached, continuing...");
        }

        // Stage 3: Small buffer to let the browser finish painting the UI
        // 200ms is enough for most SPA render cycles without slowing tests down
        page.waitForTimeout(200);
    }

    // ============================================================
    // PAGE INFO
    // ============================================================

    /**
     * Returns the current page's title (the text shown in the browser tab).
     * Useful for assertions that verify the correct page is loaded.
     *
     * @return — The page title as a String
     */
    public String getPageTitle() {
        String title = page.title();
        logger.debug("Current Page title: {}", title);
        return title;
    }

    /**
     * Returns the raw Playwright Page object.
     * Use this when you need direct Playwright access for advanced interactions
     * not covered by ElementUtils methods.
     *
     * @return — The active Playwright Page instance
     */
    public Page getPage() {
        return page;
    }

}