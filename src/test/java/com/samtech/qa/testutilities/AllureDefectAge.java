package com.samtech.qa.testutilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AllureDefectAge {
    public static void main(String[] args) throws IOException {
        // Use argument if provided (e.g., target/regression-results)
        String path = (args.length > 0) ? args[0] : "target/allure-results";
        File folder = new File(path);
        if (!folder.exists()) return;

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "target/defect-age-report_" + ts + ".csv";

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

        try (PrintWriter w = new PrintWriter(new FileWriter(fileName))) {
            w.println("Class_Name,Test_Name,Defect_Age_Builds,First_Failed_Date,Error_Message");
            for (var entry : defects.entrySet()) {
                int age = 1;
                String[] d = entry.getValue();
                long firstFailTime = Long.parseLong(d[3]);
                if (hr != null && hr.has(entry.getKey())) {
                    List<JsonNode> items = new ArrayList<>();
                    hr.get(entry.getKey()).path("items").forEach(items::add);
                    items.sort((a, b) -> Long.compare(b.path("time").path("stop").asLong(0), a.path("time").path("stop").asLong(0)));
                    for (JsonNode item : items) {
                        if (item.path("status").asText("").matches("failed|broken")) {
                            age++;
                            firstFailTime = item.path("time").path("start").asLong(firstFailTime);
                        } else break;
                    }
                }
                String date = LocalDateTime.ofInstant(Instant.ofEpochMilli(firstFailTime), ZoneId.systemDefault()).format(dtf);
                w.println(d[0] + "," + d[1] + "," + age + "," + date + "," + d[2]);
            }
        }
    }
}
