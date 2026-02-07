//package com.samtech.qa.runners;
//
//import io.cucumber.testng.AbstractTestNGCucumberTests;
//import io.cucumber.testng.CucumberOptions;
//import org.testng.SkipException;
//import org.testng.annotations.BeforeClass;
//import org.testng.annotations.DataProvider;
//
//import java.io.File;
//
//@CucumberOptions(
//        features = "@target/failed_scenarios.txt", // Note the '@' - it reads the file content
//        glue = {"com.samtech.qa.stepdefinitions", "com.samtech.qa.hooks"},
//        plugin = {
//                "pretty",
//                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",
//                "html:target/cucumber-reports/cucumber.html",
//                "json:target/cucumber-reports/cucumber.json",
//        }
//)
//public class FailedTestRunner extends AbstractTestNGCucumberTests {
//
//    @BeforeClass
//    public void skipIfNoFailures() {
//        File rerunFile = new File("target/failed_scenarios.txt");
//        // If file doesn't exist or is completely empty, skip this runner
//        if (!rerunFile.exists() || rerunFile.length() == 0) {
//            System.out.println(">>> No failed scenarios found to rerun. Skipping FailedTestRunner.");
//            throw new SkipException("No failed scenarios to rerun.");
//        }
//    }
//
//    @Override
//    @DataProvider(parallel = true)
//    public Object[][] scenarios() {
//        return super.scenarios();
//    }
//}
