package com.samtech.qa.pages;

import com.microsoft.playwright.Page;
import com.samtech.qa.testutilities.AllureEnvironmentManager;
import com.samtech.qa.utils.ElementUtils;
import org.testng.annotations.BeforeSuite;

public class BasePage {
    protected ElementUtils elementUtils;
    protected Page page;

    public BasePage(ElementUtils elementUtils) {
        this.elementUtils = elementUtils;
        this.page = elementUtils.getPage();
    }
    public void navigateTo(String url) {
        page.navigate(url);
        elementUtils.waitForPageLoad();
    }
}
