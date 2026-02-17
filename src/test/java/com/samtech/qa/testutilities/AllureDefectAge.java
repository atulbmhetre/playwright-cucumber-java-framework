package com.samtech.qa.testutilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AllureDefectAge {

    private static final String RESULTS_PATH = "target/allure-results";

    public static void main(String[] args) throws IOException {

        File folder = new File(RESULTS_PATH);
        if (!folder.exists()) return;

        ObjectMapper mapper = new ObjectMapper();
        File[] files = folder.listFiles((dir, name) -> name.endsWith("-result.json"));
        if (files == null || files.length == 0) return;

        Map<String, String[]> defects = new HashMap<>();

        for (File file : files) {
            JsonNode result = mapper.readTree(file);
            String status = result.path("status").asText("");

            if (status.equals("failed") || status.equals("broken")) {

                String historyId = result.path("historyId").asText("unknown");
                String className = result.path("fullName").asText("N/A");
                String testName = result.path("name").asText("N/A");

                String errorMsg = result.path("statusDetails")
                        .path("message")
                        .asText("No Message")
                        .replace(",", ";")
                        .replace("\n", " ");

                long startTime = result.path("start").asLong(0);

                defects.put(historyId, new String[]{
                        className,
                        testName,
                        errorMsg,
                        String.valueOf(startTime)
                });
            }
        }

        File historyFile = new File(RESULTS_PATH + "/history/history.json");
        JsonNode historyRoot = historyFile.exists() ? mapper.readTree(historyFile) : null;

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        new File("target/build-artifacts").mkdirs();
        File output = new File("target/build-artifacts/defect-age-report.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(output))) {

            writer.println("Class_Name,Test_Name,Defect_Age_Builds,First_Failed_Date,Error_Message");

            for (Map.Entry<String, String[]> entry : defects.entrySet()) {

                String historyId = entry.getKey();
                String[] data = entry.getValue();

                int age = 1;
                long firstFailureTime = Long.parseLong(data[3]);

                if (historyRoot != null && historyRoot.has(historyId)) {
                    JsonNode items = historyRoot.get(historyId).path("items");
                    for (JsonNode item : items) {
                        String itemStatus = item.path("status").asText("");
                        if (itemStatus.equals("failed") || itemStatus.equals("broken")) {
                            age++;
                            firstFailureTime =
                                    item.path("time").path("start")
                                            .asLong(firstFailureTime);
                        } else break;
                    }
                }

                String formattedDate =
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(firstFailureTime),
                                ZoneId.systemDefault()
                        ).format(formatter);

                writer.println(
                        data[0] + "," +
                                data[1] + "," +
                                age + "," +
                                formattedDate + "," +
                                data[2]
                );
            }
        }

        // ---------- ENVIRONMENT CONFIG ----------
        Properties props = new Properties();
        props.setProperty("Build_Number",
                Optional.ofNullable(System.getenv("GITHUB_RUN_NUMBER")).orElse("Local"));
        props.setProperty("Environment",
                Optional.ofNullable(System.getenv("TEST_ENV")).orElse("qa"));
        props.setProperty("Browser",
                Optional.ofNullable(System.getenv("TEST_BROWSER")).orElse("chromium"));
        props.setProperty("Execution_Type", "GitHub Actions");

        try (FileOutputStream fos =
                     new FileOutputStream(RESULTS_PATH + "/environment.properties")) {
            props.store(fos, "Execution Environment");
        }

        System.out.println("Defect age report generated successfully.");
    }
}
