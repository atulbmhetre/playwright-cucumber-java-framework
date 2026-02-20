package com.samtech.qa.pages;

import com.microsoft.playwright.Page;
import com.samtech.qa.testutilities.AllureEnvironmentManager;
import com.samtech.qa.utils.ElementUtils;
import org.testng.annotations.BeforeSuite;

/**
 * BasePage — The parent class that all Page Object classes extend.
 *
 * In the Page Object Model (POM) design pattern, each web page (or major section)
 * has its own Java class representing it. BasePage is the foundation all of those
 * classes are built on — it provides the common objects and methods every page needs.
 *
 * By extending BasePage, every page class automatically gets:
 *   - Access to ElementUtils (for clicking, typing, reading elements)
 *   - Access to the Playwright Page (for direct browser interactions)
 *   - The navigateTo() method (to open a URL and wait for the page to load)
 *
 * Example — a LoginPage extending BasePage:
 *   public class LoginPage extends BasePage {
 *       public LoginPage(ElementUtils elementUtils) {
 *           super(elementUtils);   // calls BasePage constructor
 *       }
 *       public void enterUsername(String username) {
 *           elementUtils.enterText(username, "#username");
 *       }
 *   }
 *
 * WHY "protected"?
 *   Fields are marked protected so child page classes can access them directly
 *   without needing getters — keeps page class code concise and readable.
 */
public class BasePage {

    // Available to all page classes that extend BasePage
    protected ElementUtils elementUtils;  // UI interaction helpers (click, type, getText, etc.)
    protected Page page;                  // Raw Playwright Page — for direct browser control

    /**
     * Constructor — called by every child page class via super(elementUtils).
     * Stores ElementUtils and extracts the Page from it so both are ready to use.
     *
     * @param elementUtils — The ElementUtils instance wired to the current scenario's browser tab
     */
    public BasePage(ElementUtils elementUtils) {
        this.elementUtils = elementUtils;
        this.page = elementUtils.getPage();  // Extract Page so child classes can access it directly
    }

    /**
     * Navigates the browser to the given URL and waits for the page to load.
     *
     * Used by step definitions or page methods to open a specific URL.
     * Always waits for DOMContentLoaded after navigating — ensures the page
     * structure is ready before any element interactions are attempted.
     *
     * @param url — The full URL to navigate to (e.g. "https://app.example.com/login")
     */
    public void navigateTo(String url) {
        page.navigate(url);
        elementUtils.waitForPageLoad();  // Wait for DOM to finish loading before returning
    }
}