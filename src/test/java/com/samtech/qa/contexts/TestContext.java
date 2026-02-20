package com.samtech.qa.contexts;

import com.microsoft.playwright.Page;
import com.samtech.qa.factory.DriverFactory;
import com.samtech.qa.utils.ElementUtils;

/**
 * TestContext — The shared state container for a single test scenario.
 *
 * In a Cucumber + dependency injection setup, step definition classes can't
 * easily share objects with each other (e.g. the browser Page, utility helpers).
 * TestContext solves this by acting as a single object that holds everything
 * a scenario needs — passed into each step class via the DI framework (PicoContainer).
 *
 * What it holds:
 *   - DriverFactory → responsible for launching and closing the browser
 *   - Page          → the active browser tab (created lazily on first access)
 *   - ElementUtils  → helper methods for clicking, typing, reading elements
 *   - scenarioName  → the name of the currently running Cucumber scenario
 *
 * LAZY INITIALIZATION:
 *   The browser (Page) is NOT opened in the constructor.
 *   It's opened the first time getPage() is called.
 *   This means if a scenario somehow never needs the browser, no browser is launched.
 *   It also ensures ElementUtils is always built with a valid, ready Page.
 *
 * LIFECYCLE:
 *   A new TestContext is created for each scenario by PicoContainer.
 *   This gives every scenario its own clean browser session and state —
 *   scenarios never share or pollute each other's context.
 */
public class TestContext {

    private DriverFactory driverFactory;  // Manages browser launch and teardown
    private Page page;                    // The active browser tab (null until first use)
    private ElementUtils elementUtils;    // UI interaction helpers (null until page is ready)
    private String scenarioName;          // Name of the current Cucumber scenario

    /**
     * Constructor — sets up the DriverFactory but does NOT open the browser yet.
     * The browser opens lazily on the first call to getPage().
     * Called automatically by PicoContainer at the start of each scenario.
     */
    public TestContext() {
        this.driverFactory = new DriverFactory();
    }

    /**
     * Returns the DriverFactory for this scenario.
     * Used by hooks to call closePlaywright() after the scenario finishes.
     */
    public DriverFactory getDriverFactory() {
        return driverFactory;
    }

    /**
     * Returns the active browser Page, launching the browser first if not already open.
     *
     * LAZY INITIALIZATION pattern:
     *   - First call  → opens the browser, creates the Page and ElementUtils, returns Page
     *   - Subsequent calls → returns the already-open Page immediately
     *
     * This ensures the browser is only launched when a step actually needs it,
     * and that ElementUtils is always created with the same Page instance.
     *
     * @return — The Playwright Page (active browser tab) for this scenario
     */
    public Page getPage() {
        if (this.page == null) {
            // Browser not open yet — initialize now and wire up ElementUtils at the same time
            this.page = driverFactory.initPlaywright();
            this.elementUtils = new ElementUtils(this.page);
        }
        return this.page;
    }

    /**
     * Returns the ElementUtils helper for this scenario.
     *
     * If the browser hasn't been opened yet, calling this triggers getPage()
     * first — ensuring ElementUtils always has a valid Page to work with.
     *
     * @return — The ElementUtils instance wired to this scenario's browser tab
     */
    public ElementUtils getElementUtils() {
        if (this.elementUtils == null) {
            getPage();  // Opens browser and creates ElementUtils as a side effect
        }
        return elementUtils;
    }

    /**
     * Stores the current Cucumber scenario name.
     * Called from hooks at the start of each scenario so that other components
     * (like FailedLocatorCollector) can tag failures with the correct scenario name.
     *
     * @param name — The Cucumber scenario name from the Scenario object
     */
    public void setScenarioName(String name) {
        this.scenarioName = name;
    }

    /**
     * Returns the current scenario name.
     * Used by hooks and utilities that need to reference which scenario is running.
     *
     * @return — The scenario name set at the start of this test
     */
    public String getScenarioName() {
        return this.scenarioName;
    }
}