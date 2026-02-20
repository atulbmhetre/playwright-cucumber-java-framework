package com.samtech.qa.testutilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AllureDefectAge — Generates a CSV report showing how long each failing test has been failing.
 *
 * "Defect Age" = the number of consecutive builds a test has been in a failed/broken state.
 * A defect age of 1 means it failed for the first time this build.
 * A defect age of 5 means it has failed in the last 5 consecutive builds.
 *
 * This is valuable because it helps the team prioritise which failures to investigate:
 *   - High age (failing for many builds) → likely a real, persistent bug
 *   - Age of 1 → could be a new regression or a flaky test
 *
 * HOW IT WORKS:
 *   1. Reads all Allure result JSON files from target/allure-results/
 *   2. Collects every test that is currently "failed" or "broken"
 *   3. Checks the Allure history file to count how many previous builds
 *      that same test was also failing (using historyId as the unique key)
 *   4. Finds the timestamp of the very first failure in the streak
 *   5. Writes everything to target/defect-age-report.csv
 *
 * This class is run as a standalone Java program from the CI workflow
 * using: mvn exec:java -Dexec.mainClass="...AllureDefectAge"
 *
 * OUTPUT — defect-age-report.csv columns:
 *   Class_Name        → The test class the scenario belongs to
 *   Test_Name         → The scenario/test name
 *   Defect_Age_Builds → How many consecutive builds this test has been failing
 *   First_Failed_Date → When this failure streak started
 *   Error_Message     → The failure message from the most recent run
 */
public class AllureDefectAge {

    public static void main(String[] args) throws IOException {

        String resultsPath = "target/allure-results";
        File resultsFolder = new File(resultsPath);

        // Nothing to process if the results folder doesn't exist yet
        if (!resultsFolder.exists()) {
            System.out.println("Allure results folder not found.");
            return;
        }

        // ── Step 1: Write the Allure environment file ──
        // Done first so environment info is available in the report even if the
        // defect age calculation fails partway through
        try {
            AllureEnvironmentManager.writeEnvironmentInfo();
            System.out.println("Environment file created successfully.");
        } catch (Exception e) {
            System.out.println("Failed to create environment file: " + e.getMessage());
        }

        ObjectMapper mapper = new ObjectMapper();

        // ── Step 2: Find all Allure result files from the current run ──
        // Each test scenario produces one *-result.json file in allure-results/
        File[] resultFiles = resultsFolder.listFiles((dir, name) -> name.endsWith("-result.json"));

        if (resultFiles == null) {
            System.out.println("No result files found.");
            return;
        }

        // ── Step 3: Collect all failed/broken tests from the current run ──
        // Key   = historyId (Allure's unique fingerprint per test, consistent across runs)
        // Value = [className, testName, errorMessage, startTimestamp]
        Map<String, String[]> defects = new HashMap<>();

        for (File file : resultFiles) {
            JsonNode root = mapper.readTree(file);
            String status = root.path("status").asText("");

            // Only interested in failed or broken tests
            if (status.equalsIgnoreCase("failed") || status.equalsIgnoreCase("broken")) {

                // historyId is Allure's stable unique key for a test across builds —
                // used to match this result against entries in the history file
                String historyId = root.path("historyId").asText("unknown");

                String className = root.path("fullName").asText("N/A");
                String testName  = root.path("name").asText("N/A");

                // Sanitise error message for CSV: commas → semicolons, newlines → spaces
                // (prevents the message from breaking the CSV column structure)
                String errorMessage = root.path("statusDetails")
                        .path("message")
                        .asText("No Error Message")
                        .replace(",", ";")
                        .replace("\n", " ");

                long startTime = root.path("start").asLong(0);

                defects.put(historyId, new String[]{
                        className,
                        testName,
                        errorMessage,
                        String.valueOf(startTime)
                });
            }
        }

        // ── Step 4: Load the Allure history file ──
        // history.json contains results from previous builds — used to calculate defect age.
        // If the file doesn't exist (first run or history wasn't restored), age defaults to 1.
        File historyFile = new File(resultsPath + "/history/history.json");
        JsonNode historyRoot = historyFile.exists() ? mapper.readTree(historyFile) : null;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // ── Step 5: Write the CSV report ──
        File csvFile = new File("target/defect-age-report.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {

            writer.println("Class_Name,Test_Name,Defect_Age_Builds,First_Failed_Date,Error_Message");

            for (Map.Entry<String, String[]> entry : defects.entrySet()) {

                // Start at age 1 (this build counts as the first failure in the streak)
                int age = 1;
                String[] defectData = entry.getValue();
                long firstFailTime = Long.parseLong(defectData[3]); // Current build's fail time

                // ── Step 6: Count consecutive failures in history ──
                // Walk backwards through historical builds for this test.
                // Increment age for each consecutive failed/broken build.
                // Stop counting as soon as we hit a passing build — the streak is broken.
                if (historyRoot != null && historyRoot.has(entry.getKey())) {

                    JsonNode items = historyRoot.get(entry.getKey()).path("items");

                    for (JsonNode item : items) {
                        String itemStatus = item.path("status").asText("");
                        if (itemStatus.equalsIgnoreCase("failed") || itemStatus.equalsIgnoreCase("broken")) {
                            age++;  // This historical build was also failing — extend the streak
                            // Update firstFailTime to this older build's timestamp
                            // (keeps moving the "first failure" date further back in time)
                            firstFailTime = item.path("time").path("start").asLong(firstFailTime);
                        } else {
                            break;  // Hit a passing build — streak ends here
                        }
                    }
                }

                // Convert Unix timestamp (milliseconds) to a human-readable date string
                String formattedDate = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(firstFailTime),
                        ZoneId.systemDefault()
                ).format(formatter);

                // Write one CSV row per defect
                writer.println(
                        defectData[0] + "," +  // Class_Name
                                defectData[1] + "," +  // Test_Name
                                age           + "," +  // Defect_Age_Builds
                                formattedDate + "," +  // First_Failed_Date
                                defectData[2]          // Error_Message
                );
            }
        }

        System.out.println("Defect age report generated successfully.");
    }
}