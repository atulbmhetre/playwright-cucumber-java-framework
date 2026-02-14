package com.samtech.qa.testutilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class AllureDefectAge {

    private static final String HISTORY_DIR =
            System.getProperty("history.dir", "target/allure-results/history");
    private static final String OUTPUT_FILE = "target/defect-age-report.csv"; // File for CI artifact
    private static final Set<String> DEFECT_STATUSES =
            Set.of("failed", "broken");

    public static void main(String[] args) throws Exception {
        File folder = Paths.get(HISTORY_DIR).toFile();

        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("History folder not found: " + HISTORY_DIR);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, TestHistory> testHistoryMap = new HashMap<>();

        for (File file : Objects.requireNonNull(folder.listFiles((dir, name) -> name.endsWith(".json")))) {

            JsonNode root = mapper.readTree(file);

            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();

                String historyId = entry.getKey();
                JsonNode testNode = entry.getValue();

                JsonNode items = testNode.get("items");
                if (items == null || !items.isArray()) continue;

                TestHistory testHistory = testHistoryMap.computeIfAbsent(
                        historyId,
                        k -> new TestHistory(historyId, historyId)
                );

                for (JsonNode item : items) {
                    String status = item.get("status").asText();
                    long timestamp = item.get("time").get("start").asLong();

                    testHistory.addStatus(status, timestamp);
                }
            }
        }

        List<DefectReport> defectReports = new ArrayList<>();

        for (TestHistory testHistory : testHistoryMap.values()) {
            List<StatusEntry> statuses = testHistory.getStatuses();
            // Sort by timestamp ascending
            statuses.sort(Comparator.comparingLong(StatusEntry::getTimestamp));

            // Count consecutive failures from last run backwards
            int consecutiveFailures = 0;
            long firstFailTs = 0;
            long lastFailTs = 0;

            for (int i = statuses.size() - 1; i >= 0; i--) {
                StatusEntry s = statuses.get(i);
                if (DEFECT_STATUSES.contains(s.getStatus().toLowerCase())) {
                    consecutiveFailures++;
                    lastFailTs = s.getTimestamp();
                    firstFailTs = s.getTimestamp(); // will update as we move backwards
                } else {
                    break;
                }
            }

            if (consecutiveFailures > 0) {
                int firstFailIndex = statuses.size() - consecutiveFailures;
                firstFailTs = statuses.get(firstFailIndex).getTimestamp();
                lastFailTs = statuses.get(statuses.size() - 1).getTimestamp();

                Date firstFailDate = new Date(firstFailTs);
                Date lastFailDate = new Date(lastFailTs);

                long ageDays = (lastFailDate.getTime() - firstFailDate.getTime()) / (1000 * 60 * 60 * 24) + 1;

                defectReports.add(new DefectReport(
                        testHistory.getName(),
                        testHistory.getFullName(),
                        consecutiveFailures,
                        firstFailDate,
                        lastFailDate,
                        ageDays
                ));
            }
        }

        // Sort by age descending
        defectReports.sort((a, b) -> Long.compare(b.getAgeDays(), a.getAgeDays()));

        // Prepare output
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append("Test Name,Runs Failed,Age(Days),First Failed,Last Failed\n");

        for (DefectReport d : defectReports) {
            sb.append(String.format("%s,%d,%d,%s,%s\n",
                    d.getName(),
                    d.getConsecutiveFailures(),
                    d.getAgeDays(),
                    sdf.format(d.getFirstFailed()),
                    sdf.format(d.getLastFailed())));
        }

        // Print to console
        System.out.println(sb.toString());

        // Write to file
        File outFile = new File(OUTPUT_FILE);
        outFile.getParentFile().mkdirs(); // create target folder if not exists
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
            writer.write(sb.toString());
        }

        System.out.println("Defect age report written to: " + OUTPUT_FILE);
    }
}

// Helper classes remain same
class TestHistory {
    private final String name;
    private final String fullName;
    private final List<StatusEntry> statuses = new ArrayList<>();

    public TestHistory(String name, String fullName) {
        this.name = name;
        this.fullName = fullName;
    }

    public void addStatus(String status, long timestamp) {
        statuses.add(new StatusEntry(status, timestamp));
    }

    public String getName() { return name; }
    public String getFullName() { return fullName; }
    public List<StatusEntry> getStatuses() { return statuses; }
}

class StatusEntry {
    private final String status;
    private final long timestamp;

    public StatusEntry(String status, long timestamp) {
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getStatus() { return status; }
    public long getTimestamp() { return timestamp; }
}

class DefectReport {
    private final String name;
    private final String fullName;
    private final int consecutiveFailures;
    private final Date firstFailed;
    private final Date lastFailed;
    private final long ageDays;

    public DefectReport(String name, String fullName, int consecutiveFailures, Date firstFailed, Date lastFailed, long ageDays) {
        this.name = name;
        this.fullName = fullName;
        this.consecutiveFailures = consecutiveFailures;
        this.firstFailed = firstFailed;
        this.lastFailed = lastFailed;
        this.ageDays = ageDays;
    }

    public String getName() { return name; }
    public String getFullName() { return fullName; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public Date getFirstFailed() { return firstFailed; }
    public Date getLastFailed() { return lastFailed; }
    public long getAgeDays() { return ageDays; }
}
