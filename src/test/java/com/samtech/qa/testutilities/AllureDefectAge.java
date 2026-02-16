package com.samtech.qa.testutilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;

public class AllureDefectAge {
    public static void main(String[] args) throws IOException {
        System.out.println("Defect Age csv generation started.");
        File folder = new File("target/allure-results");
        if (!folder.exists()) return;

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
                        r.path("statusDetails").path("trace").asText("No Trace").split("\n")[0].replace(",", ";")
                });
            }
        }

        File hf = new File("target/allure-results/history/history.json");
        JsonNode hr = hf.exists() ? mapper.readTree(hf) : null;

        try (PrintWriter w = new PrintWriter(new FileWriter("target/defect-age-report.csv"))) {
            w.println("Class_Name,Test_Name,Defect_Age,Error_Message,Short_StackTrace");
            for (var entry : defects.entrySet()) {
                int age = 1;
                if (hr != null && hr.has(entry.getKey())) {
                    for (JsonNode item : hr.get(entry.getKey()).path("items")) {
                        String hs = item.path("status").asText("").toLowerCase();
                        if (hs.equals("failed") || hs.equals("broken")) age++; else break;
                    }
                }
                String[] d = entry.getValue();
                w.println(d[0] + "," + d[1] + "," + age + "," + d[2] + "," + d[3]);
            }
        }
    }
}