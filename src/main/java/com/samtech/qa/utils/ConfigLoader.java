package com.samtech.qa.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigLoader {

    private final Properties properties = new Properties();
    private static final ConfigLoader loader = new ConfigLoader();
    private String environment;

    public static ConfigLoader getInstance() {
        return loader;
    }

    private ConfigLoader() {

        String env = System.getProperty("env");

        try {

            FileInputStream baseFis = new FileInputStream(
                    "src/test/resources/config/config.properties");

            properties.load(baseFis);

            // Step 1: If no system property, read from base config
            if (env == null || env.isEmpty()) {
                env = properties.getProperty("env");
                if (env == null || env.isEmpty()) {
                    throw new RuntimeException("Environment not specified. Please set -Denv or define env in config.properties");
                }
            }

            // Step 2: Load environment specific file
            FileInputStream fis = new FileInputStream(
                    "src/test/resources/config/" + env + ".config.properties");

            properties.load(fis);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load config file for env: " + env);
        }
    }

    public String getMandatoryProp(String key) {
        String value = resolve(key);

        if (value == null) {
            throw new RuntimeException(
                    "Required configuration missing for key: " + key);
        }

        return value;
    }

    public String getOptionalProp(String key) {
        return resolve(key);
    }

    private String resolve(String key) {

        String sysValue = System.getProperty(key);
        if (sysValue != null) {
            return sysValue;
        }

        String configValue = properties.getProperty(key);
        if (configValue != null) {
            return configValue;
        }

        return getDefault(key);
    }

    public String getEnvironment() {
        return environment;
    }

    // ================================
    // Central Default Values
    // ================================

    private String getDefault(String key) {

        switch (key) {
            case "headless":
                return "false";
            case "screenshot.on.scenario.failure":
                return "false";
            case "screenshot.on.scenario.success":
                return "false";
            case "screenshot.on.scenario.skipped":
                return "false";
            case "screenshot.for.step.passed":
                return "false";
            case "screenshot.for.step.failed":
                return "true";
            case "timeout.page.load":
                return "100000";
            case "timeout.global.wait":
                return "15000";
            case "timeout.default.assertion":
                return "5000";
            case "dataproviderthreadcount":
                return "2";
            default:
                return null; // no default defined
        }
    }
}
