package com.samtech.qa.stepdefinitions;

import com.samtech.qa.contexts.TestContext;
import com.samtech.qa.contexts.TestContext;
import com.samtech.qa.pages.DashboardPage;
import com.samtech.qa.pages.LoginPage;
import com.samtech.qa.testutilities.TestProofsCollection;
import com.samtech.qa.testutilities.stepStatus;
import com.samtech.qa.utils.ConfigLoader;
import com.samtech.qa.utils.ExcelUtility.DataManager;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import java.util.Map;

public class LoginSteps {
    private final TestContext testContext;
    private final LoginPage loginPage;
    private final DashboardPage dashboardPage;
    private static final Logger logger = LoggerFactory.getLogger(LoginSteps.class);
    private TestProofsCollection tc;

    // Modern Injection via PicoContainer
    public LoginSteps(TestContext testContext) {
        this.testContext = testContext;
        // Initializing pages using the shared ElementUtils from context
        this.loginPage = new LoginPage(testContext.getElementUtils());
        this.dashboardPage = new DashboardPage(testContext.getElementUtils());
        tc = new TestProofsCollection(testContext);
    }

    @Given("the user is on the login page")
    public void navigateToLoginPage() {
        loginPage.navigetToApplication();
        logger.info("User navigated to application.");
    }

    @When("the user logs into the application with user credentials")
    public void logsInWithValidCredentials() {
        try{
            String scenarioName = testContext.getScenarioName();
            Map<String, String> testData = DataManager.getTestData("Login", scenarioName);
            String user = testData.get("Username");
            String pass = testData.get("Password");
            loginPage.enterCredentials(user, pass);
            loginPage.clickLogin();
            logger.info("User logs in to application using user name : {}, password : {}", user, pass);
            tc.attachStepArtifacts(stepStatus.PASSED);
        }catch (Throwable t){
            tc.attachStepArtifacts(stepStatus.FAILED);
            throw t;
        }

    }

    @Then("the user should see the {string} overview")
    public void verifyDashboardRedirection(String expectedHeader) {
        Assert.assertTrue(dashboardPage.isDashboardVisible(), "Dashboard failed to load!");
        logger.info("Dashboard page is visible.");
        String actualHeader = dashboardPage.getHeaderText();
        Assert.assertEquals(actualHeader, expectedHeader, "Header title mismatch on Dashboard!");
        logger.info("Dashboard title is as expected.");
    }

    @Then("the user should see the {string} overview1")
    public void verifyDashboardRedirection1(String expectedHeader) {
        try {
            Assert.assertTrue(false, "Dashboard failed to load!");
            logger.info("Dashboard page is visible.");
            String actualHeader = dashboardPage.getHeaderText();
            Assert.assertEquals(actualHeader, expectedHeader, "Header title mismatch on Dashboard!");
            logger.info("Dashboard title is as expected.");
            tc.attachStepArtifacts(stepStatus.PASSED);
        }catch (Throwable t){
            tc.attachStepArtifacts(stepStatus.FAILED);
            throw t;
        }
    }

    @When("the user logs into the application with username {string} and password {string}")
    public void performLoginWithDirectData(String user, String pass) {
        // This ignores Excel and uses the strings passed directly from Gherkin
        loginPage.enterCredentials(user, pass);
        loginPage.clickLogin();
        logger.info("User logs in to application using user name : {}, password : {}", user, pass);
    }

    @Then("the user should see the {string} error message")
    public void verifyErrorMessage(String expectedError) {
        String actualError = loginPage.getErrorMessage();
        Assert.assertEquals(actualError, expectedError);
        logger.info("Expected error message is displayed and verified.");
    }

}
