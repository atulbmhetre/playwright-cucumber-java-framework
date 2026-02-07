package com.samtech.qa.utils;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samtech.qa.utils.ExcelUtility.DataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FailedLocatorCollector {
    private static final List<Map<String, Object>> failureLogs = Collections.synchronizedList(new ArrayList<>());
    private static final ThreadLocal<String> currentScenario = new ThreadLocal<>();
    private static final Logger logger = LoggerFactory.getLogger(FailedLocatorCollector.class);

    public static void setScenarioName(String name) {
        currentScenario.set(name);
    }

    public static void removeScenarioName() {
        currentScenario.remove();
    }

    public static void addFailure(String action, String locator) {
        String currentScenarioName = currentScenario.get();
        Map<String, Object> existingEntry = null;

        // 1. Simple loop to find if we already logged this locator
        synchronized (failureLogs) { // Synchronize block for safe iteration
            for (Map<String, Object> entry : failureLogs) {
                if (entry.get("locator").equals(locator)) {
                    existingEntry = entry;
                    break;
                }
            }
        }

        if (existingEntry != null) {
            // 2. If found, add the scenario to the existing list
            List<String> scenarios = (List<String>) existingEntry.get("impacted_scenarios");
            if (!scenarios.contains(currentScenarioName)) {
                scenarios.add(currentScenarioName);
            }
        } else {
            // 3. If NOT found, create a brand new entry
            Map<String, Object> newEntry = new LinkedHashMap<>();
            newEntry.put("locator", locator);
            newEntry.put("action", action);

            List<String> scenarios = new ArrayList<>();
            scenarios.add(currentScenarioName);
            newEntry.put("impacted_scenarios", scenarios);

            failureLogs.add(newEntry);
            logger.debug("Locator added to failed xpath collection, locator: {}", locator);
        }
    }

    public static void generateJsonReport() {
        if (failureLogs.isEmpty()) return;

        ObjectMapper mapper = new ObjectMapper();

        // Setup the pretty printer for the [ { } ] structure you like
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        try {
            File outputDir = new File("test-output");
            if (!outputDir.exists()) outputDir.mkdirs();

            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyyy_HHmmss"));
            String fileName = "failedLocators_" + date + ".json";

            mapper.writer(printer).writeValue(new File(outputDir, fileName), failureLogs);

            logger.debug("--- Locator Report Generated: test-output/" + fileName + " ---");
        } catch (IOException e) {
            logger.error("Jackson failed to write the Failed Locator JSON report: {}", e.getMessage());
        }
    }
}