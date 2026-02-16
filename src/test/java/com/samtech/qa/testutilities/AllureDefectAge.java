package com.samtech.qa.testutilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AllureDefectAge {
    public static void main(String[] args) throws IOException {
        File folder = new File("target/allure-results");
        if (!folder.exists()) return;

        // 1. Timestamped Filename
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "target/defect-age-report_" + ts + ".csv";

        ObjectMapper mapper = new ObjectMapper();
        File[] files = folder.listFiles((d, n) -> n.endsWith("-result.json"));
        if (files == null) return;

        Map<String, String[]> defects = new HashMap<>();
        for (File f : files) {
            JsonNode r = mapper.readTree(f);
            String status = r.path("status").asText("").toLowerCase();
            if (status.equals("failed") || status.equals("broken")) {
                String id = r.path("historyId").asText("unknown");
                defects.put(id, new String[]{
                        r.path("fullName").asText("N/A"),
                        r.path("name").asText("N/A"),
                        r.path("statusDetails").path("message").asText("No Msg").replace(",", ";").replace("\n", " "),
                        String.valueOf(r.path("start").asLong(0))
                });
            }
        }

        File hf = new File("target/allure-results/history/history.json");
        JsonNode hr = hf.exists() ? mapper.readTree(hf) : null;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (PrintWriter w = new PrintWriter(new FileWriter(fileName))) {
            w.println("Class_Name,Test_Name,Defect_Age_Builds,First_Failed_Date,Error_Message");
            for (var entry : defects.entrySet()) {
                int age = 1;
                long firstFailTime = Long.parseLong(entry.getValue()[3]);

                if (hr != null && hr.has(entry.getKey())) {
                    List<JsonNode> items = new ArrayList<>();
                    hr.get(entry.getKey()).path("items").forEach(items::add);
                    items.sort((a, b) -> Long.compare(b.path("time").path("stop").asLong(0), a.path("time").path("stop").asLong(0)));

                    for (JsonNode item : items) {
                        String hs = item.path("status").asText("").toLowerCase();
                        if (hs.equals("failed") || hs.equals("broken")) {
                            age++;
                            firstFailTime = item.path("time").path("start").asLong(firstFailTime);
                        } else break;
                    }
                }
                String dateStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(firstFailTime), ZoneId.systemDefault()).format(dtf);
                String[] d = entry.getValue();
                w.println(d[0] + "," + d[1] + "," + age + "," + dateStr + "," + d[2]);
            }
        }
        System.out.println("✅ Generated: " + fileName);
    }
}
