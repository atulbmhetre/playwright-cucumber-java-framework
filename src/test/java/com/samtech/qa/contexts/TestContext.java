package com.samtech.qa.contexts;

import com.microsoft.playwright.Page;
import com.samtech.qa.factory.DriverFactory;
import com.samtech.qa.utils.ElementUtils;

public class TestContext {

    private DriverFactory driverFactory;
    private Page page;
    private ElementUtils elementUtils;
    private String scenarioName;
    public TestContext() {
        this.driverFactory = new DriverFactory();
    }

    public DriverFactory getDriverFactory() {
        return driverFactory;
    }
    public Page getPage() {
        // If the page isn't open yet, open it now (Lazy Initialization)
        if (this.page == null) {
            this.page = driverFactory.initPlaywright();
            this.elementUtils = new ElementUtils(this.page);
        }
        return this.page;
    }
    public ElementUtils getElementUtils() {
        if (this.elementUtils == null) {
            getPage();
        }
        return elementUtils;
    }
    public void setScenarioName(String name) {
        this.scenarioName = name;
    }
    public String getScenarioName() {
        return this.scenarioName;
    }
}
