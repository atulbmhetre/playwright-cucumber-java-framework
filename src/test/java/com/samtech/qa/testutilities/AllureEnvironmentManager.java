package com.samtech.qa.testutilities;

import com.samtech.qa.utils.ConfigLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * AllureEnvironmentManager — Writes test environment details to the Allure report.
 *
 * Allure can display an "Environment" panel in the report showing key details
 * about the conditions under which the tests ran — browser, environment, URL, etc.
 * This panel is populated by a file called environment.properties in the
 * allure-results folder.
 *
 * Without this file, the Allure report has no environment panel and anyone
 * reading the report has no way to know which environment or browser was used.
 *
 * Called from:
 *   - SmokeTestsRunner / RegressionTestsRunner @BeforeSuite → written before tests start
 *   - AllureDefectAge.main() → written before the defect age CSV is generated
 *
 * Output file: target/allure-results/environment.properties
 *
 * Example content:
 *   Environment=qa
 *   Browser=chromium
 *   URL=https://qa.example.com
 *   Headless=true
 */
public class AllureEnvironmentManager {

    /**
     * Reads the current run's configuration and writes it to environment.properties.
     *
     * The file is written to target/allure-results/ — Allure reads this folder
     * when generating the HTML report and automatically picks up the properties file.
     *
     * The results directory is created if it doesn't already exist (safe for first runs).
     *
     * @throws RuntimeException if the file cannot be written (e.g. permission issue)
     */
    public static void writeEnvironmentInfo() {

        ConfigLoader config = ConfigLoader.getInstance();

        // Ensure the allure-results directory exists before trying to write into it
        File resultsDir = new File("target/allure-results");
        if (!resultsDir.exists()) {
            resultsDir.mkdirs();  // Creates the full directory path if missing
        }

        File envFile = new File(resultsDir, "environment.properties");

        // try-with-resources ensures the file is closed properly even if writing fails
        try (FileWriter writer = new FileWriter(envFile)) {

            // Each line = one key-value pair displayed in the Allure environment panel
            writer.write("Environment=" + config.getEnvironment()              + "\n");  // e.g. qa, staging
            writer.write("Browser="     + config.getMandatoryProp("browser")   + "\n");  // e.g. chromium, firefox
            writer.write("URL="         + config.getMandatoryProp("url")       + "\n");  // Base URL under test
            writer.write("Headless="    + config.getOptionalProp("headless")   + "\n");  // true = no visible browser

        } catch (IOException e) {
            throw new RuntimeException("Failed to write Allure environment file", e);
        }
    }
}