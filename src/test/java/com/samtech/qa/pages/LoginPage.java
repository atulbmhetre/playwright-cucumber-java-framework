package com.samtech.qa.pages;

import com.samtech.qa.utils.ConfigLoader;
import com.samtech.qa.utils.ElementUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.Selector;

public class LoginPage extends BasePage{

    private static final Logger logger = LoggerFactory.getLogger(LoginPage.class);

    // 1. Locators as Arrays: {Primary, Fallback1, Fallback2}
    private String[] usernameField = {"input[name='usernam']", "//input[@placeholder='Username']", "input.oxd-input"};
    private String[] passwordField = {"input[name='password']", "//input[@type='password']", "input.oxd-input--active"};
    private String[] loginButton = {"button[type='submi']", "//button[contains(.,'Login')]", ".orangehrm-login-button"};
    private String errorMessage = "//div[@class='orangehrm-login-error']//p";

    public LoginPage(ElementUtils elementUtils) {
        super(elementUtils);
    }

    // 2. Page Actions
    public void navigetToApplication(){
        String url = ConfigLoader.getInstance().getMandatoryProp("url");
        navigateTo(url);
        elementUtils.waitForPageStable();
    }
    public void enterCredentials(String user, String pass) {
        // We pass the whole array to our ElementUtils
        elementUtils.enterText(user, usernameField);
        elementUtils.enterText(pass, passwordField);
    }

    public void clickLogin() {
        elementUtils.clickElement(loginButton);
        elementUtils.waitForPageStable();
    }

    public String getErrorMessage() {
        return page.locator(errorMessage).first().textContent().trim();
    }

}
