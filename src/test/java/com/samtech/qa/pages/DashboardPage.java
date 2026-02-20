package com.samtech.qa.pages;

import com.samtech.qa.utils.ElementUtils;
import com.samtech.qa.utils.FailedLocatorCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardPage extends BasePage {

    // Locators as Arrays for Self-Healing
    private String[] dashboardHeader = {
            "h6.oxd-text--h6",
            "//h6[text()='Dashboard']",
            ".oxd-topbar-header-breadcrumb-module"
    };

    // Constructor receiving the 'Injected' utility
    public DashboardPage(ElementUtils elementUtils) {
        super(elementUtils);
    }

    public boolean isDashboardVisible() {
        elementUtils.waitForPageURL("dashboard");
        return elementUtils.isElementVisible(dashboardHeader);
    }

    public String getHeaderText() {
        return elementUtils.getElementText(dashboardHeader);
    }
}
