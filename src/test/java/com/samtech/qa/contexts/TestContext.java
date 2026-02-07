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
        this.page = driverFactory.initPlaywright();
        this.elementUtils = new ElementUtils(page);
    }

    public DriverFactory getDriverFactory() {
        return driverFactory;
    }
    public Page getPage() {
        return page;
    }
    public ElementUtils getElementUtils() {
        return elementUtils;
    }
    public void setScenarioName(String name) {
        this.scenarioName = name;
    }
    public String getScenarioName() {
        return this.scenarioName;
    }
}
