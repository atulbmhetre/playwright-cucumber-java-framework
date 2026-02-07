package com.samtech.qa.utils;

import com.samtech.qa.utils.ExcelUtility.DataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class ConfigLoader {

    private Properties properties;
    private static ConfigLoader configLoader;
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private ConfigLoader(){
        properties = new Properties();
        String mainConfigFilePath = "/src/test/resources/config/config.properties";
        loadProperties(mainConfigFilePath, true);
        String env = System.getProperty("env","dev");
        logger.info("Current execution environment: {}", env);
        String envConfigPath = "/src/test/resources/config/" + env + ".config.properties";
        loadProperties(envConfigPath, false);
    }
    private void loadProperties(String relativePath, boolean isMandatory) {
        String filePath = System.getProperty("user.dir") + relativePath;
        File file = new File(filePath);
        try (FileReader reader = new FileReader(filePath)) {
            properties.load(reader);
            logger.debug("Successfully loaded properties from: {}", relativePath);
        } catch (FileNotFoundException e) {
            if (!file.exists()) {
                if (isMandatory) {
                    throw new RuntimeException("CRITICAL ERROR: Main Config not found at " + relativePath);
                } else {
                    logger.warn("Environment config not found at {}. Using global defaults(dev config) only.", relativePath);
                }
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public static ConfigLoader getInstance(){
        if(configLoader == null)
            configLoader = new ConfigLoader();
        return configLoader;
    }

    public String getProperty(String key) {
        String propValue = properties.getProperty(key);
        if (propValue == null)
            logger.error("Warning : Property " + key + " is missing.");
        return properties.getProperty(key);
    }
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
