package com.samtech.qa.testutilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AllureDefectAge {
    public static void main(String[] args) throws IOException {
        String path = (args.length > 0) ? args[0] : "target/allure-results";
        File folder = new File(path);
        if (!folder.exists()) return;

        ObjectMapper mapper = new ObjectMapper();
        File[] files = folder.listFiles((d, n) -> n.endsWith("-result.json"));
        if (files == null) return;

        Map<String, String[]> defects = new HashMap<>();
        for (File f : files) {
            JsonNode r = mapper.readTree(f);
            if (r.path("status").asText("").matches("failed|broken")) {
                String id = r.path("historyId").asText("unknown");
                defects.put(id, new String[]{
                        r.path("fullName").asText("N/A"),
                        r.path("name").asText("N/A"),
                        r.path("statusDetails").path("message").asText("No Msg").replace(",", ";").replace("\n", " "),
                        String.valueOf(r.path("start").asLong(0))
                });
            }
        }

        File hf = new File(path + "/history/history.json");
        JsonNode hr = hf.exists() ? mapper.readTree(hf) : null;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Keep filename static for consistent artifact zipping
        try (PrintWriter w = new PrintWriter(new FileWriter("target/defect-age-report.csv"))) {
            w.println("Class_Name,Test_Name,Defect_Age_Builds,First_Failed_Date,Error_Message");
            for (var entry : defects.entrySet()) {
                int age = 1;
                String[] d = entry.getValue();
                long firstFailTime = Long.parseLong(d[3]);
                if (hr != null && hr.has(entry.getKey())) {
                    JsonNode items = hr.get(entry.getKey()).path("items");
                    if (items != null) {
                        for (JsonNode item : items) {
                            if (item.path("status").asText("").matches("failed|broken")) {
                                age++;
                                firstFailTime = item.path("time").path("start").asLong(firstFailTime);
                            } else break;
                        }
                    }
                }
                String date = LocalDateTime.ofInstant(Instant.ofEpochMilli(firstFailTime), ZoneId.systemDefault()).format(dtf);
                w.println(d[0] + "," + d[1] + "," + age + "," + date + "," + d[2]);
            }
        }

        // Inject info into Allure Environment widget
        Properties props = new Properties();
        props.setProperty("Build_Number", System.getenv("GITHUB_RUN_NUMBER") != null ? System.getenv("GITHUB_RUN_NUMBER") : "Local");
        props.setProperty("Total_Defects", String.valueOf(defects.size()));
        try (FileOutputStream fos = new FileOutputStream(path + "/environment.properties")) {
            props.store(fos, "Allure Env Info");
        }
    }
}
