package com.samtech.qa.pages;

import com.microsoft.playwright.Page;
import com.samtech.qa.utils.ElementUtils;

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
