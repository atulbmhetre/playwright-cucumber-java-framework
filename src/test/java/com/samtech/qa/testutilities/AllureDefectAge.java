package com.samtech.qa.testutilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class AllureDefectAge {

    // Default directories
    private static final String ALLURE_RESULTS_DIR = "target/allure-results";
    private static final String HISTORY_JSON = "target/allure-results/history/history.json";
    private static final String OUTPUT_CSV = "target/defect-age-report.csv";

    public static void main(String[] args) throws IOException {

        File resultsDir = new File(ALLURE_RESULTS_DIR);

        if (!resultsDir.exists() || !resultsDir.isDirectory()) {
            System.out.println("Allure results directory not found: " + ALLURE_RESULTS_DIR);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        // 1) Read current run result files
        File[] resultFiles = resultsDir.listFiles((dir, name) -> name.endsWith("-result.json"));
        if (resultFiles == null || resultFiles.length == 0) {
            System.out.println("No result files found in: " + ALLURE_RESULTS_DIR);
            return;
        }

        // Keep only latest run status/details per historyId
        Map<String, CurrentRunEntry> currentRunMap = new HashMap<>();

        for (File file : resultFiles) {
            JsonNode root = mapper.readTree(file);

            String historyId = getSafeText(root, "historyId");
            String status = normalizeStatus(getSafeText(root, "status"));
            String testName = getSafeText(root, "name");
            String fullName = getSafeText(root, "fullName");

            if (historyId == null || testName == null || status == null) {
                continue;
            }

            String className = (fullName != null && !fullName.isBlank()) ? fullName : "UnknownClass";

            currentRunMap.put(historyId, new CurrentRunEntry(className, testName, status));
        }

        // 2) Read allure history/history.json if present
        File historyFile = new File(HISTORY_JSON);
        JsonNode historyRoot = null;
        if (historyFile.exists() && historyFile.isFile()) {
            historyRoot = mapper.readTree(historyFile);
        } else {
            System.out.println("Warning: history.json not found. Defect age will be based on current run only.");
        }

        // 3) Build report rows
        List<DefectRow> rows = new ArrayList<>();

        for (Map.Entry<String, CurrentRunEntry> e : currentRunMap.entrySet()) {
            String historyId = e.getKey();
            CurrentRunEntry current = e.getValue();
            if (!isDefect(current.status)) {
                continue; // include only failed/broken tests in defect age report
            }

            int defectCount = isDefect(current.status) ? 1 : 0;
            int totalRuns = 1;
            int defectAge = isDefect(current.status) ? 1 : 0; // consecutive fail/broken streak ending now

            if (historyRoot != null && historyRoot.has(historyId)) {
                JsonNode node = historyRoot.get(historyId);

                JsonNode statistic = node.path("statistic");
                int failed = statistic.path("failed").asInt(0);
                int broken = statistic.path("broken").asInt(0);
                int passed = statistic.path("passed").asInt(0);
                int skipped = statistic.path("skipped").asInt(0);
                int unknown = statistic.path("unknown").asInt(0);

                // Add previous history counts; + current run already included above
                defectCount = failed + broken + (isDefect(current.status) ? 1 : 0);
                totalRuns = failed + broken + passed + skipped + unknown + 1;

                // Compute consecutive defect streak from latest backward
                JsonNode items = node.path("items");
                if (isDefect(current.status)) {
                    defectAge = 1; // include current run
                    List<JsonNode> historyItems = new ArrayList<>();
                    items.forEach(historyItems::add);

                    // sort descending by stop time (latest first)
                    historyItems.sort((a, b) -> Long.compare(
                            b.path("time").path("stop").asLong(0L),
                            a.path("time").path("stop").asLong(0L)
                    ));

                    for (JsonNode item : historyItems) {
                        String historicalStatus = normalizeStatus(item.path("status").asText(null));
                        if (isDefect(historicalStatus)) {
                            defectAge++;
                        } else {
                            break;
                        }
                    }
                } else {
                    defectAge = 0; // latest run is not defect -> age reset
                }
            }

            rows.add(new DefectRow(current.className, current.testName, defectCount, totalRuns, defectAge));
        }

        writeCsv(rows);

        System.out.println("====== DEFECT AGE REPORT GENERATED ======");
        System.out.println("Defect age report written to: " + OUTPUT_CSV);
    }

    private static void writeCsv(List<DefectRow> rows) throws IOException {
        try (FileWriter writer = new FileWriter(OUTPUT_CSV)) {
            writer.append("Class Name,Test Name,Defect Count,Total Runs,Defect Age\n");
            for (DefectRow r : rows) {
                writer.append(escapeCsv(r.className)).append(",")
                        .append(escapeCsv(r.testName)).append(",")
                        .append(String.valueOf(r.defectCount)).append(",")
                        .append(String.valueOf(r.totalRuns)).append(",")
                        .append(String.valueOf(r.defectAge))
                        .append("\n");
            }
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String getSafeText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private static String normalizeStatus(String status) {
        return status == null ? null : status.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isDefect(String status) {
        return "failed".equals(status) || "broken".equals(status);
    }

    private static class CurrentRunEntry {
        private final String className;
        private final String testName;
        private final String status;

        private CurrentRunEntry(String className, String testName, String status) {
            this.className = className;
            this.testName = testName;
            this.status = status;
        }
    }

    private static class DefectRow {
        private final String className;
        private final String testName;
        private final int defectCount;
        private final int totalRuns;
        private final int defectAge;

        private DefectRow(String className, String testName, int defectCount, int totalRuns, int defectAge) {
            this.className = className;
            this.testName = testName;
            this.defectCount = defectCount;
            this.totalRuns = totalRuns;
            this.defectAge = defectAge;
        }
    }
}