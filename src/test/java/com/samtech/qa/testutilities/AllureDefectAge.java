package com.samtech.qa.testutilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AllureDefectAge {

    public static void main(String[] args) throws IOException {

        String resultsPath = "target/allure-results";
        File resultsFolder = new File(resultsPath);

        if (!resultsFolder.exists()) {
            System.out.println("Allure results folder not found.");
            return;
        }

        // ✅ FIRST: Write Environment File
        try {
            AllureEnvironmentManager.writeEnvironmentInfo();
            System.out.println("Environment file created successfully.");
        } catch (Exception e) {
            System.out.println("Failed to create environment file: " + e.getMessage());
        }

        ObjectMapper mapper = new ObjectMapper();
        File[] resultFiles = resultsFolder.listFiles((dir, name) -> name.endsWith("-result.json"));

        if (resultFiles == null) {
            System.out.println("No result files found.");
            return;
        }

        Map<String, String[]> defects = new HashMap<>();

        for (File file : resultFiles) {

            JsonNode root = mapper.readTree(file);

            String status = root.path("status").asText("");

            if (status.equalsIgnoreCase("failed") || status.equalsIgnoreCase("broken")) {

                String historyId = root.path("historyId").asText("unknown");

                String className = root.path("fullName").asText("N/A");
                String testName = root.path("name").asText("N/A");

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

        File historyFile = new File(resultsPath + "/history/history.json");
        JsonNode historyRoot = historyFile.exists() ? mapper.readTree(historyFile) : null;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        File csvFile = new File("target/defect-age-report.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {

            writer.println("Class_Name,Test_Name,Defect_Age_Builds,First_Failed_Date,Error_Message");

            for (Map.Entry<String, String[]> entry : defects.entrySet()) {

                int age = 1;
                String[] defectData = entry.getValue();
                long firstFailTime = Long.parseLong(defectData[3]);

                if (historyRoot != null && historyRoot.has(entry.getKey())) {

                    JsonNode items = historyRoot.get(entry.getKey()).path("items");

                    for (JsonNode item : items) {
                        String itemStatus = item.path("status").asText("");
                        if (itemStatus.equalsIgnoreCase("failed") || itemStatus.equalsIgnoreCase("broken")) {
                            age++;
                            firstFailTime = item.path("time").path("start").asLong(firstFailTime);
                        } else {
                            break;
                        }
                    }
                }

                String formattedDate = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(firstFailTime),
                        ZoneId.systemDefault()
                ).format(formatter);

                writer.println(
                        defectData[0] + "," +
                                defectData[1] + "," +
                                age + "," +
                                formattedDate + "," +
                                defectData[2]
                );
            }
        }

        System.out.println("Defect age report generated successfully.");
    }
}
