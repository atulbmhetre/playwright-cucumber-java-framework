package com.samtech.qa.testutilities;

import com.samtech.qa.utils.ConfigLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class AllureEnvironmentManager {

    public static void writeEnvironmentInfo() {

        ConfigLoader config = ConfigLoader.getInstance();

        File resultsDir = new File("allure-results");
        if (!resultsDir.exists()) {
            resultsDir.mkdirs();
        }

        File envFile = new File(resultsDir, "environment.properties");

        try (FileWriter writer = new FileWriter(envFile)) {

            writer.write("Environment=" + config.getEnvironment() + "\n");
            writer.write("Browser=" + config.getMandatoryProp("browser") + "\n");
            writer.write("BaseURL=" + config.getMandatoryProp("base.url") + "\n");
            writer.write("Headless=" + config.getOptionalProp("headless") + "\n");

        } catch (IOException e) {
            throw new RuntimeException("Failed to write Allure environment file", e);
        }
    }
}
