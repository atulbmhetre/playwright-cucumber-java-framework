package com.samtech.qa.factory;

import com.microsoft.playwright.*;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.samtech.qa.utils.ConfigLoader;
import com.samtech.qa.utils.ExcelUtility.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Arrays;

/**
 * DriverFactory — Central class for managing Playwright browser sessions.
 *
 * Responsible for:
 *   - Launching the correct browser (Chromium, Firefox, or WebKit)
 *   - Configuring browser settings (headless mode, timeouts, video recording)
 *   - Providing a Page object that test steps use to interact with the UI
 *   - Safely closing all browser resources after each test
 *
 * Thread Safety:
 *   All browser objects (Playwright, Browser, BrowserContext, Page) are stored
 *   in ThreadLocal variables. This means each test thread gets its own isolated
 *   browser session — essential for safe parallel test execution.
 *   Without ThreadLocal, parallel tests would share the same browser and
 *   interfere with each other.
 */
public class DriverFactory {

    // Logger for printing debug/error messages to the console and log files
    private final Logger logger = LoggerFactory.getLogger(DriverFactory.class);

    // ── ThreadLocal browser objects ──────────────────────────────────────────
    // Each variable holds a separate instance per thread.
    // Think of it like each test thread having its own private copy.

    private ThreadLocal<Playwright> tlPlaywright = new ThreadLocal<>();         // The root Playwright instance
    private ThreadLocal<Browser> tlBrowser = new ThreadLocal<>();               // The launched browser (Chromium / Firefox / WebKit)
    private ThreadLocal<BrowserContext> tlBrowserContext = new ThreadLocal<>();  // An isolated browser session (like an incognito window)
    private ThreadLocal<Page> tlPage = new ThreadLocal<>();                     // A single browser tab — used by all test steps

    // ── Getters — allow other classes to access the current thread's objects ──

    public ThreadLocal<Playwright> getTlPlaywright() {
        return tlPlaywright;
    }
    public ThreadLocal<Browser> getTlBrowser() {
        return tlBrowser;
    }
    public ThreadLocal<BrowserContext> getTlBrowserContext() {
        return tlBrowserContext;
    }
    public ThreadLocal<Page> getTlPage() {
        return tlPage;
    }

    /**
     * Initializes Playwright and launches the browser for the current test thread.
     *
     * Steps performed:
     *   1. Reads browser name and headless flag from system properties or config file
     *   2. Launches the appropriate browser with standard flags
     *   3. Creates a browser context (isolated session) with video recording enabled
     *   4. Applies global timeouts for assertions, waits, and page navigation
     *   5. Opens a new page (tab) and returns it for use in tests
     *
     * @return Page — the browser tab that test steps will interact with
     */
    public Page initPlaywright(){

        // ── Read browser name from Maven CLI (-Dbrowser=chromium) or config file ──
        String browserName = System.getProperty("browser", ConfigLoader.getInstance().getMandatoryProp("browser"));
        if (browserName == null || browserName.isEmpty()) {
            logger.error("CRITICAL ERROR: Browser name not specified in Maven flag (-Dbrowser) or config files.");
            throw new RuntimeException("Missing Browser Configuration: Please define 'browser' in your properties or pass it via CLI.");
        }
        logger.debug("Browser set to test on: {}", browserName);

        // ── Read headless flag — true = browser runs invisibly (faster, for CI) ──
        Boolean isHeadless = Boolean.parseBoolean(System.getProperty("headless", ConfigLoader.getInstance().getOptionalProp("headless")));
        logger.debug("Headless flag set to: {}", isHeadless);

        // ── Start the Playwright engine for this thread ──
        tlPlaywright.set(Playwright.create());

        // ── Launch the correct browser based on the browser name ──
        // Standard flags applied to all browsers:
        //   --start-maximized        → opens browser in full screen
        //   --disable-extensions     → prevents browser extensions interfering with tests
        //   --allow-insecure-localhost → allows testing on local HTTPS without cert errors
        switch (browserName.trim().toLowerCase()){
            case "chromium":
                tlBrowser.set(tlPlaywright.get().chromium().launch(
                        new BrowserType.LaunchOptions()
                                .setHeadless(isHeadless)
                                .setArgs(Arrays.asList("--start-maximized",
                                        "--disable-extensions",
                                        "--allow-insecure-localhost")
                                ))
                );
                break;
            case "firefox":
                tlBrowser.set(tlPlaywright.get().firefox().launch(
                        new BrowserType.LaunchOptions()
                                .setHeadless(isHeadless)
                                .setArgs(Arrays.asList("--start-maximized",
                                        "--disable-extensions",
                                        "--allow-insecure-localhost")
                                ))
                );
                break;
            case "webkit":
                // WebKit is the engine behind Safari — useful for cross-browser coverage
                tlBrowser.set(tlPlaywright.get().webkit().launch(
                        new BrowserType.LaunchOptions()
                                .setHeadless(isHeadless)
                                .setArgs(Arrays.asList("--start-maximized",
                                        "--disable-extensions",
                                        "--allow-insecure-localhost")
                                ))
                );
                break;
            case "default":
                // Catches invalid/unsupported browser names
                logger.debug("Provided browser \"{}\"  is not valid or configured", browserName);
                break;
        }

        // ── Create a browser context (like an isolated incognito session) ──
        // setViewportSize(null) → disables fixed viewport, lets browser use full window size
        // setRecordVideoDir   → automatically records a video of every test run
        // setRecordVideoSize  → sets the video resolution (1280x720 = HD)
        tlBrowserContext.set(tlBrowser.get().newContext(
                new Browser.NewContextOptions().setViewportSize(null)
                        .setRecordVideoDir(Paths.get("test-output/videos/"))
                        .setRecordVideoSize(1280, 720)));

        // ── Apply global timeouts (all values come from config file) ──
        // Assertion timeout → how long Playwright waits for an assertion to pass before failing
        PlaywrightAssertions.setDefaultAssertionTimeout(Long.parseLong(ConfigLoader.getInstance().getOptionalProp("timeout.default.assertion")));
        // Global wait timeout → how long Playwright waits for any element action (click, fill, etc.)
        tlBrowserContext.get().setDefaultTimeout(Long.parseLong(ConfigLoader.getInstance().getOptionalProp("timeout.global.wait")));
        // Navigation timeout → how long to wait for a page to fully load
        tlBrowserContext.get().setDefaultNavigationTimeout(Long.parseLong(ConfigLoader.getInstance().getOptionalProp("timeout.page.load")));
        logger.debug("Default Assertion timeout, Global timeout, Navigation timeout set.");

        // ── Open a new browser tab and return it to the calling test ──
        tlPage.set(tlBrowserContext.get().newPage());
        logger.debug("Browser \"{}\" launched successfully.", browserName);
        return tlPage.get();
    }

    /**
     * Closes all Playwright resources for the current test thread.
     *
     * Resources are closed in reverse order of creation:
     *   Page → BrowserContext → Browser → Playwright
     *
     * Why this order matters:
     *   Closing in reverse ensures each layer shuts down cleanly before
     *   the parent layer is destroyed. Skipping this can cause hanging
     *   processes or incomplete video recordings.
     *
     * After closing, ThreadLocal variables are cleared (remove()) to
     * prevent memory leaks in long-running parallel test suites.
     */
    public void closePlaywright(){

        // Close the page (tab) first
        if (tlPage.get() != null)
            tlPage.get().close();
        logger.debug("Browser Page closed successfully.");

        // Close the browser context (session) — this finalizes the video recording
        if (tlBrowserContext.get() != null)
            tlBrowserContext.get().close();
        logger.debug("Browser context closed successfully.");

        // Close the browser itself
        if (tlBrowser.get()!= null)
            tlBrowser.get().close();
        logger.debug("Browser closed successfully.");

        // Shut down the Playwright engine
        if(tlPlaywright.get() != null)
            tlPlaywright.get().close();
        logger.debug("Playwright closed successfully.");

        // ── Clean up ThreadLocal variables to prevent memory leaks ──
        // In parallel runs, not removing these can cause old thread data
        // to linger in memory, leading to subtle bugs in long test suites.
        tlPage.remove();
        tlBrowserContext.remove();
        tlBrowser.remove();
        tlPlaywright.remove();
    }

}