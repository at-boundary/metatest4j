package metatest.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HtmlReportGenerator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void generateReport(String outputPath) {
        try {
            // Read JSON reports
            JsonNode faultSimulation = readJsonFile("fault_simulation_report.json");
            JsonNode gapAnalysis = readJsonFile("gap_analysis.json");
            JsonNode schemaCoverage = readJsonFile("schema_coverage.json");

            // Generate HTML
            String html = buildHtmlReport(faultSimulation, gapAnalysis, schemaCoverage);

            // Write to file
            try (FileWriter writer = new FileWriter(outputPath)) {
                writer.write(html);
            }

            System.out.println("[MetaTest] HTML report generated: " + outputPath);

        } catch (Exception e) {
            System.err.println("[MetaTest] Failed to generate HTML report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static JsonNode readJsonFile(String filename) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        return OBJECT_MAPPER.readTree(file);
    }

    private static String buildHtmlReport(JsonNode faultSimulation, JsonNode gapAnalysis, JsonNode schemaCoverage) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>MetaTest Report</title>\n");
        html.append("  <style>\n");
        html.append(getCssStyles());
        html.append("  </style>\n");
        html.append("</head>\n<body>\n");

        // Header
        html.append(buildHeader());

        // Summary Cards
        html.append(buildSummaryCards(faultSimulation, gapAnalysis, schemaCoverage));

        // Navigation Tabs
        html.append("  <div class=\"tabs\">\n");
        html.append("    <button class=\"tab-button active\" onclick=\"showTab('fault-simulation')\">Fault Simulation</button>\n");
        html.append("    <button class=\"tab-button\" onclick=\"showTab('gap-analysis')\">Execution Coverage</button>\n");
        html.append("    <button class=\"tab-button\" onclick=\"showTab('test-matrix')\">Test Matrix</button>\n");
//        html.append("    <button class=\"tab-button\" onclick=\"showTab('schema-coverage')\">Schema Coverage</button>\n");
        html.append("  </div>\n");

        // Tab Content
        html.append("  <div id=\"fault-simulation\" class=\"tab-content active\">\n");
        html.append(buildFaultSimulationSection(faultSimulation));
        html.append("  </div>\n");

        html.append("  <div id=\"gap-analysis\" class=\"tab-content\">\n");
        html.append(buildGapAnalysisSection(gapAnalysis));
        html.append("  </div>\n");

        html.append("  <div id=\"test-matrix\" class=\"tab-content\">\n");
        html.append(buildTestMatrixSection(faultSimulation));
        html.append("  </div>\n");

//        html.append("  <div id=\"schema-coverage\" class=\"tab-content\">\n");
//        html.append(buildSchemaCoverageSection(schemaCoverage));
//        html.append("  </div>\n");

        // JavaScript
        html.append("  <script>\n");
        html.append(getJavaScript());
        html.append("  </script>\n");

        html.append("</body>\n</html>");

        return html.toString();
    }

    private static String buildHeader() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return "  <div class=\"header\">\n" +
               "    <div class=\"header-content\">\n" +
               "      <div>\n" +
               "        <h1>MetaTest Report</h1>\n" +
               "        <p class=\"subtitle\">REST API Tests Effectiveness Results</p>\n" +
               "        <p class=\"timestamp\">Generated: " + timestamp + "</p>\n" +
               "      </div>\n" +
               "      <button class=\"theme-toggle\" onclick=\"toggleTheme()\" title=\"Toggle theme\">\n" +
               "        <span class=\"theme-icon\">🌙</span>\n" +
               "      </button>\n" +
               "    </div>\n" +
               "  </div>\n";
    }

    private static String buildSummaryCards(JsonNode faultSimulation, JsonNode gapAnalysis, JsonNode schemaCoverage) {
        // Calculate metrics
        int totalEndpoints = faultSimulation != null ? faultSimulation.size() : 0;
        int[] faultStats = calculateFaultStats(faultSimulation);
        // [total, detected, escaped, invariantTotal, invariantDetected, invariantEscaped]
        int totalFaults = faultStats[0];
        int detectedFaults = faultStats[1];
        int escapedFaults = faultStats[2];
        int invariantTotal = faultStats[3];
        int invariantDetected = faultStats[4];
        int invariantEscaped = faultStats[5];

        // Combined stats for overall detection rate
        int allTotal = totalFaults + invariantTotal;
        int allDetected = detectedFaults + invariantDetected;
        int allEscaped = escapedFaults + invariantEscaped;
        double detectionRate = allTotal > 0 ? (allDetected * 100.0 / allTotal) : 0;

        int untestedEndpoints = 0;
        double coveragePercentage = 0;
        if (gapAnalysis != null && gapAnalysis.has("summary")) {
            JsonNode summary = gapAnalysis.get("summary");
            if (summary.has("untested_endpoints")) {
                untestedEndpoints = summary.get("untested_endpoints").asInt();
            }
            if (summary.has("coverage_percentage")) {
                coveragePercentage = summary.get("coverage_percentage").asDouble();
            }
        }

        StringBuilder cards = new StringBuilder();
        cards.append("  <div class=\"summary-cards\">\n");

        // Card 1: Overall Detection Rate
        String rateClass = detectionRate >= 90 ? "good" : detectionRate >= 70 ? "warning" : "bad";
        cards.append("    <div class=\"card\">\n");
        cards.append("      <div class=\"card-title\">Overall Detection Rate</div>\n");
        cards.append("      <div class=\"card-value " + rateClass + "\">" + String.format("%.1f%%", detectionRate) + "</div>\n");
        cards.append("      <div class=\"card-subtitle\">" + allDetected + " of " + allTotal + " faults detected</div>\n");
        cards.append("    </div>\n");

        // Card 2: Contract Faults
        double contractRate = totalFaults > 0 ? (detectedFaults * 100.0 / totalFaults) : 0;
        String contractClass = contractRate >= 90 ? "good" : contractRate >= 70 ? "warning" : "bad";
        cards.append("    <div class=\"card\">\n");
        cards.append("      <div class=\"card-title\">Contract Faults</div>\n");
        cards.append("      <div class=\"card-value " + contractClass + "\">" + String.format("%.0f%%", contractRate) + "</div>\n");
        cards.append("      <div class=\"card-subtitle\">" + detectedFaults + "/" + totalFaults + " detected (" + escapedFaults + " escaped)</div>\n");
        cards.append("    </div>\n");

        // Card 3: Invariant Violations
        if (invariantTotal > 0) {
            double invariantRate = (invariantDetected * 100.0 / invariantTotal);
            String invariantClass = invariantRate >= 90 ? "good" : invariantRate >= 70 ? "warning" : "bad";
            cards.append("    <div class=\"card\">\n");
            cards.append("      <div class=\"card-title\">Invariant Violations</div>\n");
            cards.append("      <div class=\"card-value " + invariantClass + "\">" + String.format("%.0f%%", invariantRate) + "</div>\n");
            cards.append("      <div class=\"card-subtitle\">" + invariantDetected + "/" + invariantTotal + " detected (" + invariantEscaped + " escaped)</div>\n");
            cards.append("    </div>\n");
        } else {
            // Card 3: Endpoint Coverage (when no invariants)
            String covClass = coveragePercentage >= 80 ? "good" : coveragePercentage >= 50 ? "warning" : "bad";
            cards.append("    <div class=\"card\">\n");
            cards.append("      <div class=\"card-title\">Endpoint Coverage</div>\n");
            cards.append("      <div class=\"card-value " + covClass + "\">" + String.format("%.1f%%", coveragePercentage) + "</div>\n");
            cards.append("      <div class=\"card-subtitle\">" + untestedEndpoints + " endpoints untested</div>\n");
            cards.append("    </div>\n");
        }

        // Card 4: Tested Endpoints
        cards.append("    <div class=\"card\">\n");
        cards.append("      <div class=\"card-title\">Tested Endpoints</div>\n");
        cards.append("      <div class=\"card-value\">" + totalEndpoints + "</div>\n");
        cards.append("      <div class=\"card-subtitle\">With fault injection</div>\n");
        cards.append("    </div>\n");

        cards.append("  </div>\n");

        return cards.toString();
    }

    private static int[] calculateFaultStats(JsonNode faultSimulation) {
        // Returns: [total, detected, escaped, invariantTotal, invariantDetected, invariantEscaped]
        int total = 0;
        int detected = 0;
        int escaped = 0;
        int invariantTotal = 0;
        int invariantDetected = 0;
        int invariantEscaped = 0;

        if (faultSimulation == null || faultSimulation.isNull()) {
            return new int[]{0, 0, 0, 0, 0, 0};
        }

        Iterator<Map.Entry<String, JsonNode>> endpoints = faultSimulation.fields();
        while (endpoints.hasNext()) {
            Map.Entry<String, JsonNode> endpoint = endpoints.next();
            JsonNode endpointData = endpoint.getValue();

            // Process contract_faults: faultType -> field -> result
            if (endpointData.has("contract_faults")) {
                JsonNode contractFaults = endpointData.get("contract_faults");
                Iterator<Map.Entry<String, JsonNode>> faultTypeIter = contractFaults.fields();
                while (faultTypeIter.hasNext()) {
                    Map.Entry<String, JsonNode> faultTypeEntry = faultTypeIter.next();
                    JsonNode fields = faultTypeEntry.getValue();

                    Iterator<Map.Entry<String, JsonNode>> fieldIter = fields.fields();
                    while (fieldIter.hasNext()) {
                        Map.Entry<String, JsonNode> fieldEntry = fieldIter.next();
                        JsonNode faultData = fieldEntry.getValue();

                        boolean caughtByAny = faultData.has("caught_by_any_test") &&
                                            faultData.get("caught_by_any_test").asBoolean();
                        total++;
                        if (caughtByAny) {
                            detected++;
                        } else {
                            escaped++;
                        }
                    }
                }
            }

            // Process invariant_faults: invariantName -> result
            if (endpointData.has("invariant_faults")) {
                JsonNode invariantFaults = endpointData.get("invariant_faults");
                Iterator<Map.Entry<String, JsonNode>> invariantIter = invariantFaults.fields();
                while (invariantIter.hasNext()) {
                    Map.Entry<String, JsonNode> invariantEntry = invariantIter.next();
                    JsonNode faultData = invariantEntry.getValue();

                    boolean caughtByAny = faultData.has("caught_by_any_test") &&
                                        faultData.get("caught_by_any_test").asBoolean();
                    invariantTotal++;
                    if (caughtByAny) {
                        invariantDetected++;
                    } else {
                        invariantEscaped++;
                    }
                }
            }
        }

        return new int[]{total, detected, escaped, invariantTotal, invariantDetected, invariantEscaped};
    }

    private static String buildFaultSimulationSection(JsonNode faultSimulation) {
        if (faultSimulation == null || faultSimulation.isNull() || faultSimulation.size() == 0) {
            return "    <div class=\"empty-state\">No fault simulation data available</div>\n";
        }

        StringBuilder section = new StringBuilder();
        section.append("    <div class=\"section-title\">Fault Simulation Results</div>\n");
        section.append("    <div class=\"section-subtitle\">Showing which faults were detected or escaped by your tests</div>\n");

        int endpointIndex = 0;
        Iterator<Map.Entry<String, JsonNode>> endpoints = faultSimulation.fields();
        while (endpoints.hasNext()) {
            Map.Entry<String, JsonNode> endpoint = endpoints.next();
            String endpointPath = endpoint.getKey();
            JsonNode endpointData = endpoint.getValue();

            // Calculate summary for this endpoint
            int contractFaults = 0, contractDetected = 0, contractEscaped = 0;
            int invariantFaults = 0, invariantDetected = 0, invariantEscaped = 0;

            // Count contract faults
            if (endpointData.has("contract_faults")) {
                JsonNode contractFaultsNode = endpointData.get("contract_faults");
                Iterator<Map.Entry<String, JsonNode>> faultTypeIter = contractFaultsNode.fields();
                while (faultTypeIter.hasNext()) {
                    Map.Entry<String, JsonNode> faultTypeEntry = faultTypeIter.next();
                    JsonNode fields = faultTypeEntry.getValue();

                    Iterator<Map.Entry<String, JsonNode>> fieldIter = fields.fields();
                    while (fieldIter.hasNext()) {
                        Map.Entry<String, JsonNode> fieldEntry = fieldIter.next();
                        JsonNode faultData = fieldEntry.getValue();
                        boolean caught = faultData.has("caught_by_any_test") &&
                                        faultData.get("caught_by_any_test").asBoolean();
                        contractFaults++;
                        if (caught) contractDetected++; else contractEscaped++;
                    }
                }
            }

            // Count invariant faults
            if (endpointData.has("invariant_faults")) {
                JsonNode invariantFaultsNode = endpointData.get("invariant_faults");
                Iterator<Map.Entry<String, JsonNode>> invariantIter = invariantFaultsNode.fields();
                while (invariantIter.hasNext()) {
                    Map.Entry<String, JsonNode> invariantEntry = invariantIter.next();
                    JsonNode faultData = invariantEntry.getValue();
                    boolean caught = faultData.has("caught_by_any_test") &&
                                    faultData.get("caught_by_any_test").asBoolean();
                    invariantFaults++;
                    if (caught) invariantDetected++; else invariantEscaped++;
                }
            }

            int totalFaults = contractFaults + invariantFaults;
            int detectedFaults = contractDetected + invariantDetected;
            int escapedFaults = contractEscaped + invariantEscaped;

            section.append("    <div class=\"endpoint-card\">\n");
            section.append("      <div class=\"endpoint-header collapsible\" onclick=\"toggleEndpoint(" + endpointIndex + ")\">\n");
            section.append("        <div class=\"endpoint-title-section\">\n");
            section.append("          <span class=\"endpoint-path\">" + escapeHtml(endpointPath) + "</span>\n");
            section.append("          <div class=\"endpoint-summary\">\n");
            if (invariantFaults > 0) {
                section.append("            <span class=\"summary-badge detected\">" + contractDetected + "/" + contractFaults + " contract</span>\n");
                section.append("            <span class=\"summary-badge invariant\">" + invariantDetected + "/" + invariantFaults + " invariant</span>\n");
            } else {
                section.append("            <span class=\"summary-badge detected\">" + detectedFaults + " detected</span>\n");
            }
            section.append("            <span class=\"summary-badge escaped\">" + escapedFaults + " escaped</span>\n");
            section.append("          </div>\n");
            section.append("        </div>\n");
            section.append("        <span class=\"collapse-icon collapsed\">▼</span>\n");
            section.append("      </div>\n");
            section.append("      <div id=\"endpoint-" + endpointIndex + "\" class=\"endpoint-content collapsed\">\n");

            // Build fault table
            section.append("      <div class=\"fault-table\">\n");
            section.append("        <div class=\"fault-table-header\">\n");
            section.append("          <div class=\"fault-cell\">Field</div>\n");
            section.append("          <div class=\"fault-cell\">Fault Type</div>\n");
            section.append("          <div class=\"fault-cell\">Status</div>\n");
            section.append("          <div class=\"fault-cell\">Details</div>\n");
            section.append("        </div>\n");

            // Render contract faults: faultType -> field -> result
            if (endpointData.has("contract_faults")) {
                JsonNode contractFaultsNode = endpointData.get("contract_faults");
                Iterator<Map.Entry<String, JsonNode>> faultTypeIter = contractFaultsNode.fields();
                while (faultTypeIter.hasNext()) {
                    Map.Entry<String, JsonNode> faultTypeEntry = faultTypeIter.next();
                    String faultType = faultTypeEntry.getKey();
                    JsonNode fields = faultTypeEntry.getValue();

                    Iterator<Map.Entry<String, JsonNode>> fieldIter = fields.fields();
                    while (fieldIter.hasNext()) {
                        Map.Entry<String, JsonNode> fieldEntry = fieldIter.next();
                        String fieldName = fieldEntry.getKey();
                        JsonNode faultData = fieldEntry.getValue();

                        boolean caught = faultData.has("caught_by_any_test") &&
                                        faultData.get("caught_by_any_test").asBoolean();

                        // Get tested_by and caught_by
                        JsonNode testedBy = faultData.get("tested_by");
                        JsonNode caughtBy = faultData.get("caught_by");
                        int testedCount = testedBy != null && testedBy.isArray() ? testedBy.size() : 0;
                        int caughtCount = caughtBy != null && caughtBy.isArray() ? caughtBy.size() : 0;

                        section.append("        <div class=\"fault-row\">\n");
                        section.append("          <div class=\"fault-cell\"><code>" + escapeHtml(fieldName) + "</code></div>\n");
                        section.append("          <div class=\"fault-cell\"><span class=\"fault-badge\">" + escapeHtml(faultType) + "</span></div>\n");
                        section.append("          <div class=\"fault-cell\">");
                        section.append("<span class=\"status-badge " + (caught ? "detected" : "escaped") + "\">");
                        section.append(caught ? "✓ Detected" : "✗ Escaped");
                        section.append("</span></div>\n");
                        section.append("          <div class=\"fault-cell\">");
                        section.append("<button class=\"details-btn\" onclick=\"toggleDetails(this)\">View " + testedCount + " test(s)</button>");
                        section.append("<div class=\"test-details\" style=\"display:none;\">");

                        // Show all tests that tested this mutation
                        if (testedBy != null && testedBy.isArray()) {
                            for (JsonNode testName : testedBy) {
                                String test = testName.asText();
                                // Check if this test caught the fault
                                boolean testCaught = false;
                                String error = null;
                                if (caughtBy != null && caughtBy.isArray()) {
                                    for (JsonNode caughtDetail : caughtBy) {
                                        if (caughtDetail.has("test") && caughtDetail.get("test").asText().equals(test)) {
                                            testCaught = true;
                                            if (caughtDetail.has("error") && !caughtDetail.get("error").isNull()) {
                                                error = caughtDetail.get("error").asText();
                                            }
                                            break;
                                        }
                                    }
                                }

                                section.append("<div class=\"test-detail-item\">");
                                section.append("<span class=\"test-name\">" + escapeHtml(test) + "</span>");
                                section.append("<span class=\"status-badge " + (testCaught ? "detected" : "escaped") + "\">");
                                section.append(testCaught ? "✓" : "✗");
                                section.append("</span>");
                                if (error != null) {
                                    section.append("<div class=\"error-message\">" + escapeHtml(error) + "</div>");
                                }
                                section.append("</div>");
                            }
                        }

                        section.append("</div>");
                        section.append("</div>\n");
                        section.append("        </div>\n");
                    }
                }
            }

            // Render invariant faults: invariantName -> result
            if (endpointData.has("invariant_faults")) {
                JsonNode invariantFaultsNode = endpointData.get("invariant_faults");
                Iterator<Map.Entry<String, JsonNode>> invariantIter = invariantFaultsNode.fields();
                while (invariantIter.hasNext()) {
                    Map.Entry<String, JsonNode> invariantEntry = invariantIter.next();
                    String invariantName = invariantEntry.getKey();
                    JsonNode faultData = invariantEntry.getValue();

                    boolean caught = faultData.has("caught_by_any_test") &&
                                    faultData.get("caught_by_any_test").asBoolean();

                    // Get tested_by and caught_by
                    JsonNode testedBy = faultData.get("tested_by");
                    JsonNode caughtBy = faultData.get("caught_by");
                    int testedCount = testedBy != null && testedBy.isArray() ? testedBy.size() : 0;

                    section.append("        <div class=\"fault-row\">\n");
                    section.append("          <div class=\"fault-cell\"><code>-</code></div>\n");
                    section.append("          <div class=\"fault-cell\"><span class=\"fault-badge invariant-badge\">" + escapeHtml(invariantName) + "</span></div>\n");
                    section.append("          <div class=\"fault-cell\">");
                    section.append("<span class=\"status-badge " + (caught ? "detected" : "escaped") + "\">");
                    section.append(caught ? "✓ Detected" : "✗ Escaped");
                    section.append("</span></div>\n");
                    section.append("          <div class=\"fault-cell\">");
                    section.append("<button class=\"details-btn\" onclick=\"toggleDetails(this)\">View " + testedCount + " test(s)</button>");
                    section.append("<div class=\"test-details\" style=\"display:none;\">");

                    // Show all tests that tested this invariant
                    if (testedBy != null && testedBy.isArray()) {
                        for (JsonNode testName : testedBy) {
                            String test = testName.asText();
                            // Check if this test caught the fault
                            boolean testCaught = false;
                            String error = null;
                            if (caughtBy != null && caughtBy.isArray()) {
                                for (JsonNode caughtDetail : caughtBy) {
                                    if (caughtDetail.has("test") && caughtDetail.get("test").asText().equals(test)) {
                                        testCaught = true;
                                        if (caughtDetail.has("error") && !caughtDetail.get("error").isNull()) {
                                            error = caughtDetail.get("error").asText();
                                        }
                                        break;
                                    }
                                }
                            }

                            section.append("<div class=\"test-detail-item\">");
                            section.append("<span class=\"test-name\">" + escapeHtml(test) + "</span>");
                            section.append("<span class=\"status-badge " + (testCaught ? "detected" : "escaped") + "\">");
                            section.append(testCaught ? "✓" : "✗");
                            section.append("</span>");
                            if (error != null) {
                                section.append("<div class=\"error-message\">" + escapeHtml(error) + "</div>");
                            }
                            section.append("</div>");
                        }
                    }

                    section.append("</div>");
                    section.append("</div>\n");
                    section.append("        </div>\n");
                }
            }

            section.append("      </div>\n");
            section.append("      </div>\n"); // Close endpoint-content
            section.append("    </div>\n"); // Close endpoint-card

            endpointIndex++;
        }

        return section.toString();
    }

    private static String buildGapAnalysisSection(JsonNode gapAnalysis) {
        if (gapAnalysis == null || gapAnalysis.isNull() || gapAnalysis.size() == 0) {
            return "    <div class=\"empty-state\">No gap analysis data available</div>\n";
        }

        StringBuilder section = new StringBuilder();
        section.append("    <div class=\"section-title\">Executed Endpoints</div>\n");
        section.append("    <div class=\"section-subtitle\">Number of endpoints executions based on OpenAPI spec</div>\n");
        section.append("    <div class=\"section-subtitle\">This is not representative of your functional coverage. Use it as a proxy to identify potential coverage issues</div>\n");

        // Summary
        if (gapAnalysis.has("summary")) {
            JsonNode summary = gapAnalysis.get("summary");
            section.append("    <div class=\"gap-summary\">\n");
            section.append("      <div class=\"gap-stat\">\n");
            section.append("        <span class=\"gap-label\">Total Endpoints:</span>\n");
            section.append("        <span class=\"gap-value\">" + summary.get("total_endpoints_in_spec").asInt() + "</span>\n");
            section.append("      </div>\n");
            section.append("      <div class=\"gap-stat\">\n");
            section.append("        <span class=\"gap-label\">Tested:</span>\n");
            section.append("        <span class=\"gap-value good\">" + summary.get("tested_endpoints").asInt() + "</span>\n");
            section.append("      </div>\n");
            section.append("      <div class=\"gap-stat\">\n");
            section.append("        <span class=\"gap-label\">Untested:</span>\n");
            section.append("        <span class=\"gap-value warning\">" + summary.get("untested_endpoints").asInt() + "</span>\n");
            section.append("      </div>\n");
            section.append("      <div class=\"gap-stat\">\n");
            section.append("        <span class=\"gap-label\">Coverage:</span>\n");
            section.append("        <span class=\"gap-value\">" + String.format("%.1f%%", summary.get("coverage_percentage").asDouble()) + "</span>\n");
            section.append("      </div>\n");
            section.append("    </div>\n");
        }

        // Sub-tabs for Tested/Untested
        section.append("    <div class=\"sub-tabs\">\n");
        section.append("      <button class=\"sub-tab-button active\" onclick=\"showSubTab('tested-endpoints')\">Tested Endpoints</button>\n");
        section.append("      <button class=\"sub-tab-button\" onclick=\"showSubTab('untested-endpoints')\">Untested Endpoints</button>\n");
        section.append("    </div>\n");

        // Tested endpoints sub-tab
        section.append("    <div id=\"tested-endpoints\" class=\"sub-tab-content active\">\n");
        section.append(buildTestedEndpointsSection(gapAnalysis));
        section.append("    </div>\n");

        // Untested endpoints sub-tab
        section.append("    <div id=\"untested-endpoints\" class=\"sub-tab-content\">\n");
        section.append(buildUntestedEndpointsSection(gapAnalysis));
        section.append("    </div>\n");

        return section.toString();
    }

    private static String buildTestedEndpointsSection(JsonNode gapAnalysis) {
        StringBuilder section = new StringBuilder();

        // Tested endpoints
        if (gapAnalysis.has("tested_endpoints")) {
            JsonNode tested = gapAnalysis.get("tested_endpoints");
            if (tested.isArray() && tested.size() > 0) {
                section.append("    <div class=\"endpoint-list\">\n");

                int endpointIndex = 0;
                for (JsonNode endpoint : tested) {
                    String path = endpoint.has("path") ? endpoint.get("path").asText() : "";
                    String method = endpoint.has("method") ? endpoint.get("method").asText() : "";
                    JsonNode tests = endpoint.has("tests") ? endpoint.get("tests") : null;
                    int callCount = endpoint.has("call_count") ? endpoint.get("call_count").asInt() : 0;
                    int testCount = (tests != null && tests.isArray()) ? tests.size() : 0;

                    section.append("      <div class=\"endpoint-card gap-endpoint-card\">\n");
                    section.append("        <div class=\"endpoint-header collapsible\" onclick=\"toggleGapEndpoint('tested-" + endpointIndex + "')\">\n");
                    section.append("          <div class=\"endpoint-title-section\">\n");
                    section.append("            <div class=\"endpoint-main\">\n");
                    section.append("              <span class=\"http-method method-" + method.toLowerCase() + "\">" + method + "</span>\n");
                    section.append("              <span class=\"endpoint-path\">" + escapeHtml(path) + "</span>\n");
                    section.append("            </div>\n");
                    section.append("            <div class=\"endpoint-summary\">\n");
                    section.append("              <span class=\"summary-badge total\">" + testCount + " test(s)</span>\n");
                    section.append("              <span class=\"summary-badge total\">" + callCount + " call(s)</span>\n");
                    section.append("            </div>\n");
                    section.append("          </div>\n");
                    section.append("          <span class=\"collapse-icon collapsed\">▼</span>\n");
                    section.append("        </div>\n");
                    section.append("        <div id=\"tested-" + endpointIndex + "\" class=\"endpoint-content collapsed\">\n");

                    if (tests != null && tests.isArray() && tests.size() > 0) {
                        section.append("          <div class=\"test-list-expanded\">\n");
                        for (JsonNode test : tests) {
                            section.append("            <span class=\"test-tag\">" + escapeHtml(test.asText()) + "</span>\n");
                        }
                        section.append("          </div>\n");
                    }

                    section.append("        </div>\n");
                    section.append("      </div>\n");
                    endpointIndex++;
                }

                section.append("    </div>\n");
            } else {
                section.append("    <div class=\"empty-state\">No tested endpoints found</div>\n");
            }
        }

        return section.toString();
    }

    private static String buildUntestedEndpointsSection(JsonNode gapAnalysis) {
        StringBuilder section = new StringBuilder();

        // Untested endpoints
        if (gapAnalysis.has("untested_endpoints")) {
            JsonNode untested = gapAnalysis.get("untested_endpoints");
            if (untested.isArray() && untested.size() > 0) {
                section.append("    <div class=\"endpoint-list\">\n");

                int endpointIndex = 0;
                for (JsonNode endpoint : untested) {
                    String path = endpoint.has("path") ? endpoint.get("path").asText() : "";
                    String method = endpoint.has("method") ? endpoint.get("method").asText() : "";

                    section.append("      <div class=\"endpoint-card gap-endpoint-card untested-card\">\n");
                    section.append("        <div class=\"endpoint-header collapsible\" onclick=\"toggleGapEndpoint('untested-" + endpointIndex + "')\">\n");
                    section.append("          <div class=\"endpoint-title-section\">\n");
                    section.append("            <div class=\"endpoint-main\">\n");
                    section.append("              <span class=\"http-method method-" + method.toLowerCase() + "\">" + method + "</span>\n");
                    section.append("              <span class=\"endpoint-path\">" + escapeHtml(path) + "</span>\n");
                    section.append("            </div>\n");
                    section.append("            <div class=\"endpoint-summary\">\n");
//                    section.append("              <span class=\"summary-badge escaped\">Not Tested</span>\n");
                    section.append("            </div>\n");
                    section.append("          </div>\n");
                    section.append("          <span class=\"collapse-icon collapsed\">▼</span>\n");
                    section.append("        </div>\n");
                    section.append("        <div id=\"untested-" + endpointIndex + "\" class=\"endpoint-content collapsed\">\n");
                    section.append("          <div class=\"untested-info\">\n");
                    section.append("            <p>This endpoint is defined in the OpenAPI specification but has no test coverage.</p>\n");
                    section.append("            <p>Consider adding tests to improve API coverage.</p>\n");
                    section.append("          </div>\n");
                    section.append("        </div>\n");
                    section.append("      </div>\n");
                    endpointIndex++;
                }

                section.append("    </div>\n");
            } else {
                section.append("    <div class=\"empty-state\">All endpoints are tested! Great job!</div>\n");
            }
        }

        return section.toString();
    }

    // ── Test Matrix ───────────────────────────────────────────────────────────

    private static String buildTestMatrixSection(JsonNode faultSimulation) {
        if (faultSimulation == null || faultSimulation.isNull() || faultSimulation.size() == 0) {
            return "    <div class=\"empty-state\">No fault simulation data available</div>\n";
        }

        // Collect all unique test names in insertion order
        Set<String> testNameSet = new LinkedHashSet<>();
        faultSimulation.elements().forEachRemaining(epData -> {
            if (epData.has("contract_faults")) {
                epData.get("contract_faults").elements()
                        .forEachRemaining(ftNode -> ftNode.elements()
                                .forEachRemaining(fd -> extractTestNames(fd, testNameSet)));
            }
            if (epData.has("invariant_faults")) {
                epData.get("invariant_faults").elements()
                        .forEachRemaining(fd -> extractTestNames(fd, testNameSet));
            }
        });
        List<String> testNames = new ArrayList<>(testNameSet);
        if (testNames.isEmpty()) {
            return "    <div class=\"empty-state\">No test data available for matrix</div>\n";
        }

        StringBuilder s = new StringBuilder();
        s.append("    <div class=\"section-title\">Test \u00d7 Fault Matrix</div>\n");
        s.append("    <div class=\"section-subtitle\">"
                + "\u2713 = fault detected by test &nbsp;&nbsp;"
                + "\u2717 = fault escaped test &nbsp;&nbsp;"
                + "&ndash; = test did not exercise this endpoint</div>\n");
        s.append("    <div class=\"matrix-wrapper\">\n");
        s.append("    <table class=\"matrix-table\">\n");
        s.append("      <thead><tr>\n");
        s.append("        <th class=\"matrix-fault-col\">Endpoint / Fault</th>\n");
        for (String t : testNames) {
            s.append("        <th class=\"matrix-test-header\">"
                    + "<span class=\"matrix-test-name\">" + escapeHtml(t) + "</span></th>\n");
        }
        s.append("      </tr></thead>\n");
        s.append("      <tbody>\n");

        Iterator<Map.Entry<String, JsonNode>> endpoints = faultSimulation.fields();
        while (endpoints.hasNext()) {
            Map.Entry<String, JsonNode> epEntry = endpoints.next();
            String epPath = epEntry.getKey();
            JsonNode epData = epEntry.getValue();

            int faultCount = 0;
            if (epData.has("contract_faults")) {
                Iterator<Map.Entry<String, JsonNode>> it = epData.get("contract_faults").fields();
                while (it.hasNext()) faultCount += it.next().getValue().size();
            }
            if (epData.has("invariant_faults")) faultCount += epData.get("invariant_faults").size();
            if (faultCount == 0) continue;

            s.append("        <tr class=\"matrix-endpoint-row\">\n");
            s.append("          <td colspan=\"" + (testNames.size() + 1)
                    + "\" class=\"matrix-endpoint-label\">" + escapeHtml(epPath) + "</td>\n");
            s.append("        </tr>\n");

            if (epData.has("contract_faults")) {
                Iterator<Map.Entry<String, JsonNode>> ftIter = epData.get("contract_faults").fields();
                while (ftIter.hasNext()) {
                    Map.Entry<String, JsonNode> ftEntry = ftIter.next();
                    String faultType = ftEntry.getKey();
                    Iterator<Map.Entry<String, JsonNode>> fieldIter = ftEntry.getValue().fields();
                    while (fieldIter.hasNext()) {
                        Map.Entry<String, JsonNode> fe = fieldIter.next();
                        s.append(renderMatrixRow(faultType, fe.getKey(), false,
                                fe.getValue(), testNames));
                    }
                }
            }

            if (epData.has("invariant_faults")) {
                Iterator<Map.Entry<String, JsonNode>> invIter = epData.get("invariant_faults").fields();
                while (invIter.hasNext()) {
                    Map.Entry<String, JsonNode> ie = invIter.next();
                    s.append(renderMatrixRow(null, ie.getKey(), true,
                            ie.getValue(), testNames));
                }
            }
        }

        s.append("      </tbody>\n");
        s.append("    </table>\n");
        s.append("    </div>\n");
        return s.toString();
    }

    private static void extractTestNames(JsonNode faultData, Set<String> names) {
        JsonNode tb = faultData.path("tested_by");
        if (tb.isArray()) tb.forEach(n -> names.add(n.asText()));
    }

    private static String renderMatrixRow(String faultType, String label, boolean isInvariant,
            JsonNode faultData, List<String> testNames) {
        Set<String> testedSet = new HashSet<>();
        Set<String> caughtSet = new HashSet<>();
        JsonNode tb = faultData.path("tested_by");
        if (tb.isArray()) tb.forEach(n -> testedSet.add(n.asText()));
        JsonNode cb = faultData.path("caught_by");
        if (cb.isArray()) cb.forEach(n -> { if (n.has("test")) caughtSet.add(n.get("test").asText()); });

        StringBuilder row = new StringBuilder();
        row.append("        <tr class=\"matrix-fault-row\">\n");
        row.append("          <td class=\"matrix-fault-label\">");
        if (isInvariant) {
            row.append("<span class=\"fault-badge invariant-badge\">" + escapeHtml(label) + "</span>");
        } else {
            row.append("<span class=\"fault-badge\">" + escapeHtml(faultType) + "</span>&nbsp;");
            row.append("<code>" + escapeHtml(label) + "</code>");
        }
        row.append("</td>\n");

        for (String t : testNames) {
            String css, sym;
            if      (!testedSet.contains(t)) { css = "not-tested"; sym = "&ndash;"; }
            else if (caughtSet.contains(t))  { css = "caught";     sym = "\u2713"; }
            else                              { css = "escaped";    sym = "\u2717"; }
            row.append("          <td class=\"matrix-cell " + css + "\">" + sym + "</td>\n");
        }
        row.append("        </tr>\n");
        return row.toString();
    }

    private static String buildSchemaCoverageSection(JsonNode schemaCoverage) {
        if (schemaCoverage == null || schemaCoverage.isNull() || !schemaCoverage.has("paths")) {
            return "    <div class=\"empty-state\">No schema coverage data available</div>\n";
        }

        StringBuilder section = new StringBuilder();
        section.append("    <div class=\"section-title\">Schema Coverage Details</div>\n");
        section.append("    <div class=\"section-subtitle\">Detailed HTTP calls captured during test execution</div>\n");

        JsonNode paths = schemaCoverage.get("paths");
        Iterator<Map.Entry<String, JsonNode>> pathIter = paths.fields();

        int pathIndex = 0;
        while (pathIter.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathIter.next();
            String path = pathEntry.getKey();
            JsonNode methods = pathEntry.getValue();

            // Calculate summary for this path
            int methodCount = 0;
            int totalCalls = 0;
            int totalTests = 0;
            Iterator<Map.Entry<String, JsonNode>> summaryMethodIter = methods.fields();
            while (summaryMethodIter.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = summaryMethodIter.next();
                JsonNode methodData = methodEntry.getValue();
                methodCount++;
                if (methodData.has("summary")) {
                    JsonNode summary = methodData.get("summary");
                    totalCalls += summary.has("no_of_times_called") ? summary.get("no_of_times_called").asInt() : 0;
                    totalTests += summary.has("no_of_tests_calling") ? summary.get("no_of_tests_calling").asInt() : 0;
                }
            }

            section.append("    <div class=\"endpoint-card\">\n");
            section.append("      <div class=\"endpoint-header collapsible\" onclick=\"toggleSchemaEndpoint(" + pathIndex + ")\">\n");
            section.append("        <div class=\"endpoint-title-section\">\n");
            section.append("          <span class=\"endpoint-path\">" + escapeHtml(path) + "</span>\n");
            section.append("          <div class=\"endpoint-summary\">\n");
            section.append("            <span class=\"summary-badge total\">" + methodCount + " method(s)</span>\n");
            section.append("            <span class=\"summary-badge total\">" + totalCalls + " call(s)</span>\n");
            section.append("            <span class=\"summary-badge total\">" + totalTests + " test(s)</span>\n");
            section.append("          </div>\n");
            section.append("        </div>\n");
            section.append("        <span class=\"collapse-icon collapsed\">▼</span>\n");
            section.append("      </div>\n");
            section.append("      <div id=\"schema-endpoint-" + pathIndex + "\" class=\"endpoint-content collapsed\">\n");

            Iterator<Map.Entry<String, JsonNode>> methodIter = methods.fields();
            while (methodIter.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methodIter.next();
                String method = methodEntry.getKey();
                JsonNode methodData = methodEntry.getValue();

                section.append("        <div class=\"method-section\">\n");
                section.append("          <h4 class=\"method-title\"><span class=\"http-method method-" + method.toLowerCase() + "\">" + method + "</span></h4>\n");

                if (methodData.has("summary")) {
                    JsonNode summary = methodData.get("summary");
                    section.append("          <div class=\"coverage-stats\">\n");
                    section.append("            <span class=\"stat\">Calls: <strong>" + summary.get("no_of_times_called").asInt() + "</strong></span>\n");
                    section.append("            <span class=\"stat\">Tests: <strong>" + summary.get("no_of_tests_calling").asInt() + "</strong></span>\n");
                    section.append("          </div>\n");
                }

                if (methodData.has("calls") && methodData.get("calls").isArray()) {
                    JsonNode calls = methodData.get("calls");
                    section.append("          <div class=\"calls-container\">\n");

                    int callIndex = 0;
                    for (JsonNode call : calls) {
                        String testName = call.has("test") ? call.get("test").asText() : "Unknown";
                        int statusCode = call.has("response_status_code") ? call.get("response_status_code").asInt() : 0;
                        String statusClass = statusCode >= 200 && statusCode < 300 ? "success" :
                                           statusCode >= 400 ? "error" : "info";

                        section.append("            <div class=\"call-item\">\n");
                        section.append("              <div class=\"call-header\" onclick=\"toggleCall('call-" + pathIndex + "-" + callIndex + "')\">\n");
                        section.append("                <span class=\"test-name\">" + escapeHtml(testName) + "</span>\n");
                        section.append("                <span class=\"status-code " + statusClass + "\">" + statusCode + "</span>\n");
                        section.append("              </div>\n");
                        section.append("              <div id=\"call-" + pathIndex + "-" + callIndex + "\" class=\"call-details\" style=\"display:none;\">\n");

                        if (call.has("body") && !call.get("body").isNull()) {
                            section.append("                <div class=\"call-detail-section\">\n");
                            section.append("                  <strong>Request Body:</strong>\n");
                            section.append("                  <pre>" + escapeHtml(call.get("body").asText()) + "</pre>\n");
                            section.append("                </div>\n");
                        }

                        if (call.has("response_body") && !call.get("response_body").isNull()) {
                            section.append("                <div class=\"call-detail-section\">\n");
                            section.append("                  <strong>Response Body:</strong>\n");
                            section.append("                  <pre>" + escapeHtml(formatJson(call.get("response_body").asText())) + "</pre>\n");
                            section.append("                </div>\n");
                        }

                        section.append("              </div>\n");
                        section.append("            </div>\n");

                        callIndex++;
                    }

                    section.append("          </div>\n");
                }

                section.append("        </div>\n");
            }

            section.append("      </div>\n");
            section.append("    </div>\n");

            pathIndex++;
        }

        return section.toString();
    }

    private static String formatJson(String json) {
        try {
            Object obj = OBJECT_MAPPER.readValue(json, Object.class);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

    private static String getCssStyles() {
        return """
:root {
    --bg-primary: #fafafa;
    --bg-secondary: #ffffff;
    --card-bg: #ffffff;
    --text-primary: #18181b;
    --text-secondary: #71717a;
    --text-tertiary: #a1a1aa;
    --border-color: #e4e4e7;
    --hover-bg: #f4f4f5;
    --accent-primary: #6366f1;
    --accent-success: #10b981;
    --accent-warning: #f59e0b;
    --accent-error: #ef4444;
    --detected-bg: #d1fae5;
    --detected-text: #047857;
    --escaped-bg: #fee2e2;
    --escaped-text: #dc2626;
}

[data-theme="dark"] {
    --bg-primary: #09090b;
    --bg-secondary: #18181b;
    --card-bg: #18181b;
    --text-primary: #fafafa;
    --text-secondary: #a1a1aa;
    --text-tertiary: #71717a;
    --border-color: #27272a;
    --hover-bg: #27272a;
    --accent-primary: #818cf8;
    --accent-success: #34d399;
    --accent-warning: #fbbf24;
    --accent-error: #f87171;
    --detected-bg: #064e3b;
    --detected-text: #6ee7b7;
    --escaped-bg: #7f1d1d;
    --escaped-text: #fca5a5;
}

* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
    background: var(--bg-primary);
    min-height: 100vh;
    padding: 20px;
    color: var(--text-primary);
    transition: background 0.2s, color 0.2s;
}

.header {
    margin-bottom: 40px;
    padding: 40px 20px;
    background: var(--card-bg);
    border-bottom: 1px solid var(--border-color);
}

.header-content {
    max-width: 1400px;
    margin: 0 auto;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.header h1 {
    font-size: 2.5em;
    font-weight: 700;
    margin-bottom: 8px;
    color: var(--text-primary);
    letter-spacing: -0.02em;
}

.subtitle {
    font-size: 1em;
    color: var(--text-secondary);
    margin-bottom: 4px;
}

.timestamp {
    font-size: 0.875em;
    color: var(--text-tertiary);
}

.theme-toggle {
    background: var(--hover-bg);
    border: 1px solid var(--border-color);
    border-radius: 8px;
    width: 48px;
    height: 48px;
    cursor: pointer;
    transition: all 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.5em;
}

.theme-toggle:hover {
    background: var(--border-color);
}

.summary-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
    gap: 20px;
    margin-bottom: 30px;
    max-width: 1400px;
    margin-left: auto;
    margin-right: auto;
}

.card {
    background: var(--card-bg);
    border-radius: 12px;
    padding: 24px;
    transition: border-color 0.2s;
    border: 1px solid var(--border-color);
}

.card:hover {
    border-color: var(--accent-primary);
}

.card-title {
    font-size: 0.875em;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 12px;
    font-weight: 500;
}

.card-value {
    font-size: 2.5em;
    font-weight: 700;
    margin-bottom: 8px;
    line-height: 1;
}

.card-value.good {
    color: var(--accent-success);
}
.card-value.warning {
    color: var(--accent-warning);
}
.card-value.bad {
    color: var(--accent-error);
}

.card-subtitle {
    font-size: 0.875em;
    color: var(--text-secondary);
}

.tabs {
    display: flex;
    gap: 10px;
    margin-bottom: 30px;
    max-width: 1400px;
    margin-left: auto;
    margin-right: auto;
}

.tab-button {
    flex: 1;
    padding: 12px 24px;
    background: transparent;
    border: none;
    border-bottom: 2px solid transparent;
    border-radius: 0;
    font-size: 0.9375em;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
    color: var(--text-secondary);
}

.tab-button:hover {
    color: var(--text-primary);
    border-bottom-color: var(--border-color);
}

.tab-button.active {
    color: var(--accent-primary);
    border-bottom-color: var(--accent-primary);
    font-weight: 600;
}

.tab-content {
    display: none;
    max-width: 1400px;
    margin: 0 auto;
}

.tab-content.active {
    display: block;
}

.sub-tabs {
    display: flex;
    gap: 8px;
    margin-bottom: 24px;
    background: var(--card-bg);
    padding: 8px;
    border-radius: 12px;
    border: 1px solid var(--border-color);
}

.sub-tab-button {
    padding: 8px 16px;
    background: transparent;
    border: none;
    border-radius: 8px;
    font-size: 0.875em;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
    color: var(--text-secondary);
}

.sub-tab-button:hover {
    background: var(--hover-bg);
    color: var(--text-primary);
}

.sub-tab-button.active {
    background: var(--accent-primary);
    color: white;
    font-weight: 600;
}

.sub-tab-content {
    display: none;
}

.sub-tab-content.active {
    display: block;
}

.section-title {
    font-size: 1.5em;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 8px;
    letter-spacing: -0.01em;
}

.section-subtitle {
    font-size: 0.9375em;
    color: var(--text-secondary);
    margin-bottom: 24px;
}

.endpoint-card {
    background: var(--card-bg);
    border-radius: 12px;
    padding: 0;
    margin-bottom: 16px;
    border: 1px solid var(--border-color);
    transition: border-color 0.2s;
}

.endpoint-card:hover {
    border-color: var(--accent-primary);
}

.endpoint-header {
    border-bottom: 1px solid var(--border-color);
    padding-bottom: 16px;
    margin-bottom: 20px;
}

.endpoint-header.collapsible {
    cursor: pointer;
    display: flex;
    justify-content: space-between;
    align-items: center;
    transition: background 0.2s;
    padding: 20px;
    margin: 0;
    border-bottom: 1px solid var(--border-color);
}

.endpoint-header.collapsible:hover {
    background: var(--hover-bg);
}

.endpoint-title-section {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.endpoint-summary {
    display: flex;
    gap: 8px;
    margin-top: 8px;
}

.summary-badge {
    display: inline-block;
    padding: 4px 10px;
    border-radius: 6px;
    font-size: 0.75em;
    font-weight: 600;
}

.summary-badge.detected {
    background: var(--detected-bg);
    color: var(--detected-text);
}

.summary-badge.escaped {
    background: var(--escaped-bg);
    color: var(--escaped-text);
}

.summary-badge.total {
    background: var(--hover-bg);
    color: var(--text-secondary);
    border: 1px solid var(--border-color);
}

.summary-badge.invariant {
    background: #e0e7ff;
    color: #3730a3;
    border: 1px solid #c7d2fe;
}

[data-theme="dark"] .summary-badge.invariant {
    background: #312e81;
    color: #a5b4fc;
    border: 1px solid #4338ca;
}

.collapse-icon {
    font-size: 1.2em;
    transition: transform 0.2s;
    color: var(--text-secondary);
}

.collapse-icon.collapsed {
    transform: rotate(-90deg);
}

.endpoint-content {
    padding: 20px;
    transition: max-height 0.2s ease, opacity 0.2s;
    max-height: 5000px;
    opacity: 1;
    overflow: hidden;
}

.endpoint-content.collapsed {
    max-height: 0;
    opacity: 0;
    padding: 0 20px;
}

.endpoint-path {
    font-size: 1.3em;
    font-weight: 600;
    color: var(--text-primary);
    font-family: 'Courier New', monospace;
}

.fault-table {
    display: flex;
    flex-direction: column;
    gap: 1px;
    background: var(--border-color);
    border-radius: 8px;
    overflow: hidden;
}

.fault-table-header {
    display: grid;
    grid-template-columns: 1.5fr 1.5fr 1fr 1fr;
    background: var(--hover-bg);
    font-weight: 600;
    color: var(--text-primary);
    font-size: 0.85em;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.fault-row {
    display: grid;
    grid-template-columns: 1.5fr 1.5fr 1fr 1fr;
    background: var(--card-bg);
}

.fault-cell {
    padding: 16px;
    display: flex;
    align-items: center;
}

.fault-badge {
    display: inline-block;
    padding: 4px 12px;
    background: var(--hover-bg);
    color: var(--text-secondary);
    border-radius: 6px;
    font-size: 0.85em;
    font-weight: 500;
    text-transform: uppercase;
    border: 1px solid var(--border-color);
}

.fault-badge.invariant-badge {
    background: #e0e7ff;
    color: #3730a3;
    border: 1px solid #c7d2fe;
    text-transform: none;
}

[data-theme="dark"] .fault-badge.invariant-badge {
    background: #312e81;
    color: #a5b4fc;
    border: 1px solid #4338ca;
}

.status-badge {
    display: inline-block;
    padding: 6px 12px;
    border-radius: 6px;
    font-size: 0.85em;
    font-weight: 600;
}

.status-badge.detected {
    background: var(--detected-bg);
    color: var(--detected-text);
}

.status-badge.escaped {
    background: var(--escaped-bg);
    color: var(--escaped-text);
}

.details-btn {
    padding: 6px 12px;
    background: var(--accent-primary);
    color: white;
    border: none;
    border-radius: 6px;
    cursor: pointer;
    font-size: 0.85em;
    font-weight: 500;
    transition: opacity 0.2s;
}

.details-btn:hover {
    opacity: 0.85;
}

.test-details {
    margin-top: 12px;
    padding: 12px;
    background: var(--hover-bg);
    border-radius: 6px;
}

.test-detail-item {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 12px;
    background: var(--card-bg);
    border-radius: 6px;
    margin-bottom: 8px;
    border: 1px solid var(--border-color);
}

.test-detail-item:last-child {
    margin-bottom: 0;
}

.test-name {
    flex: 1;
    font-family: 'Courier New', monospace;
    font-size: 0.9em;
    color: var(--text-primary);
}

.error-message {
    flex: 1 0 100%;
    margin-top: 8px;
    padding: 8px;
    background: var(--escaped-bg);
    border-left: 3px solid var(--accent-error);
    border-radius: 4px;
    font-size: 0.85em;
    color: var(--escaped-text);
    font-family: 'Courier New', monospace;
}

.gap-summary {
    display: flex;
    gap: 30px;
    background: var(--card-bg);
    padding: 24px;
    border-radius: 12px;
    margin-bottom: 30px;
    border: 1px solid var(--border-color);
}

.gap-stat {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.gap-label {
    font-size: 0.9em;
    color: var(--text-secondary);
    font-weight: 500;
}

.gap-value {
    font-size: 2em;
    font-weight: 700;
    color: var(--text-primary);
}

.gap-section {
    background: var(--card-bg);
    padding: 24px;
    border-radius: 12px;
    margin-bottom: 20px;
    border: 1px solid var(--border-color);
}

.gap-section-title {
    font-size: 1.3em;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 16px;
}

.endpoint-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.endpoint-item {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
    background: var(--hover-bg);
    border-radius: 8px;
    border-left: 4px solid var(--border-color);
}

.endpoint-item.tested {
    border-left-color: var(--accent-success);
}

.endpoint-item.untested {
    border-left-color: var(--accent-error);
}

.endpoint-main {
    display: flex;
    align-items: center;
    gap: 12px;
}

.http-method {
    display: inline-block;
    padding: 6px 12px;
    border-radius: 6px;
    font-size: 0.8em;
    font-weight: 700;
    text-transform: uppercase;
    min-width: 60px;
    text-align: center;
}

.method-get { background: #dbeafe; color: #1e40af; }
.method-post { background: var(--detected-bg); color: var(--detected-text); }
.method-put { background: #fef3c7; color: #92400e; }
.method-delete { background: var(--escaped-bg); color: var(--escaped-text); }
.method-patch { background: #e0e7ff; color: #3730a3; }

.call-count {
    margin-left: auto;
    font-size: 0.9em;
    color: var(--text-secondary);
    font-weight: 500;
}

.test-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-left: 72px;
}

.test-tag {
    display: inline-block;
    padding: 4px 10px;
    background: var(--card-bg);
    color: var(--text-secondary);
    border-radius: 4px;
    font-size: 0.8em;
    font-family: 'Courier New', monospace;
    border: 1px solid var(--border-color);
}

.method-section {
    margin-bottom: 24px;
    padding: 16px;
    background: var(--hover-bg);
    border-radius: 8px;
}

.method-title {
    font-size: 1.1em;
    margin-bottom: 12px;
}

.coverage-stats {
    display: flex;
    gap: 20px;
    margin-bottom: 16px;
    font-size: 0.9em;
}

.stat {
    color: var(--text-secondary);
}

.calls-container {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.call-item {
    background: var(--card-bg);
    border-radius: 6px;
    overflow: hidden;
    border: 1px solid var(--border-color);
}

.call-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    cursor: pointer;
    transition: background 0.2s;
}

.call-header:hover {
    background: var(--hover-bg);
}

.status-code {
    padding: 4px 12px;
    border-radius: 4px;
    font-size: 0.85em;
    font-weight: 600;
}

.status-code.success { background: var(--detected-bg); color: var(--detected-text); }
.status-code.error { background: var(--escaped-bg); color: var(--escaped-text); }
.status-code.info { background: #dbeafe; color: #1e40af; }

.call-details {
    padding: 16px;
    background: var(--bg-primary);
    border-top: 1px solid var(--border-color);
}

.call-detail-section {
    margin-bottom: 16px;
}

.call-detail-section:last-child {
    margin-bottom: 0;
}

.call-detail-section strong {
    display: block;
    margin-bottom: 8px;
    color: var(--text-primary);
}

.call-detail-section pre {
    background: var(--card-bg);
    padding: 12px;
    border-radius: 6px;
    border: 1px solid var(--border-color);
    overflow-x: auto;
    font-size: 0.85em;
    line-height: 1.6;
}

code {
    font-family: 'Courier New', monospace;
    font-size: 0.9em;
    padding: 2px 6px;
    background: var(--hover-bg);
    border-radius: 4px;
    border: 1px solid var(--border-color);
}

.empty-state {
    text-align: center;
    padding: 60px 20px;
    background: var(--card-bg);
    border-radius: 12px;
    color: var(--text-secondary);
    font-size: 1.1em;
    border: 1px solid var(--border-color);
}

.gap-endpoint-card {
    margin-bottom: 12px;
}

.untested-card {
    border-left: 4px solid var(--accent-error);
}

.test-list-expanded {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding: 16px;
}

.untested-info {
    padding: 16px;
    color: var(--text-secondary);
}

.untested-info p {
    margin-bottom: 8px;
    line-height: 1.6;
}

.untested-info p:last-child {
    margin-bottom: 0;
}

/* ── Test Matrix ──────────────────────────────────────────────────────── */

.matrix-wrapper {
    overflow-x: auto;
    border-radius: 12px;
    border: 1px solid var(--border-color);
    margin-bottom: 24px;
}

.matrix-table {
    width: 100%;
    border-collapse: collapse;
    background: var(--card-bg);
    font-size: 0.875em;
}

.matrix-fault-col {
    min-width: 260px;
    padding: 12px 16px;
    background: var(--hover-bg);
    font-weight: 600;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    font-size: 0.8em;
    border-bottom: 2px solid var(--border-color);
    border-right: 2px solid var(--border-color);
    text-align: left;
}

.matrix-test-header {
    padding: 8px 6px;
    background: var(--hover-bg);
    border-bottom: 2px solid var(--border-color);
    border-right: 1px solid var(--border-color);
    min-width: 90px;
    max-width: 130px;
    vertical-align: bottom;
    text-align: center;
}

.matrix-test-name {
    display: inline-block;
    font-family: 'Courier New', monospace;
    font-size: 0.8em;
    font-weight: 600;
    color: var(--text-primary);
    writing-mode: vertical-lr;
    transform: rotate(180deg);
    white-space: nowrap;
    padding: 4px 2px;
    max-height: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
}

.matrix-endpoint-row td {
    padding: 8px 16px;
    background: var(--hover-bg);
    font-weight: 700;
    font-family: 'Courier New', monospace;
    font-size: 0.9em;
    color: var(--accent-primary);
    border-top: 2px solid var(--border-color);
    border-bottom: 1px solid var(--border-color);
}

.matrix-fault-label {
    padding: 10px 16px;
    border-right: 2px solid var(--border-color);
    border-bottom: 1px solid var(--border-color);
}

.matrix-fault-row:last-child .matrix-fault-label,
.matrix-fault-row:last-child .matrix-cell {
    border-bottom: none;
}

.matrix-cell {
    text-align: center;
    padding: 10px 6px;
    font-weight: 700;
    font-size: 1em;
    border-right: 1px solid var(--border-color);
    border-bottom: 1px solid var(--border-color);
}

.matrix-cell.caught {
    background: var(--detected-bg);
    color: var(--detected-text);
}

.matrix-cell.escaped {
    background: var(--escaped-bg);
    color: var(--escaped-text);
}

.matrix-cell.not-tested {
    color: var(--text-tertiary);
    background: var(--bg-primary);
}
""";
    }

    private static String getJavaScript() {
        return """
function showTab(tabId) {
    // Hide all tabs
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });

    // Remove active class from all buttons
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.classList.remove('active');
    });

    // Show selected tab
    document.getElementById(tabId).classList.add('active');

    // Add active class to clicked button
    event.target.classList.add('active');
}

function showSubTab(subTabId) {
    // Hide all sub-tabs
    document.querySelectorAll('.sub-tab-content').forEach(tab => {
        tab.classList.remove('active');
    });

    // Remove active class from all sub-tab buttons
    document.querySelectorAll('.sub-tab-button').forEach(btn => {
        btn.classList.remove('active');
    });

    // Show selected sub-tab
    document.getElementById(subTabId).classList.add('active');

    // Add active class to clicked button
    event.target.classList.add('active');
}

function toggleEndpoint(index) {
    const content = document.getElementById('endpoint-' + index);
    const icon = event.currentTarget.querySelector('.collapse-icon');

    if (content.classList.contains('collapsed')) {
        content.classList.remove('collapsed');
        icon.classList.remove('collapsed');
    } else {
        content.classList.add('collapsed');
        icon.classList.add('collapsed');
    }
}

function toggleDetails(btn) {
    const details = btn.nextElementSibling;
    if (details.style.display === 'none') {
        details.style.display = 'block';
        btn.textContent = btn.textContent.replace('View', 'Hide');
    } else {
        details.style.display = 'none';
        btn.textContent = btn.textContent.replace('Hide', 'View');
    }
}

function toggleCall(id) {
    const details = document.getElementById(id);
    if (details.style.display === 'none') {
        details.style.display = 'block';
    } else {
        details.style.display = 'none';
    }
}

function toggleGapEndpoint(id) {
    const content = document.getElementById(id);
    const icon = event.currentTarget.querySelector('.collapse-icon');

    if (content.classList.contains('collapsed')) {
        content.classList.remove('collapsed');
        icon.classList.remove('collapsed');
    } else {
        content.classList.add('collapsed');
        icon.classList.add('collapsed');
    }
}

function toggleSchemaEndpoint(index) {
    const content = document.getElementById('schema-endpoint-' + index);
    const icon = event.currentTarget.querySelector('.collapse-icon');

    if (content.classList.contains('collapsed')) {
        content.classList.remove('collapsed');
        icon.classList.remove('collapsed');
    } else {
        content.classList.add('collapsed');
        icon.classList.add('collapsed');
    }
}

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    const icon = document.querySelector('.theme-icon');

    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);

    // Update icon
    icon.textContent = newTheme === 'dark' ? '☀️' : '🌙';
}

// Initialize theme from localStorage
document.addEventListener('DOMContentLoaded', function() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    const icon = document.querySelector('.theme-icon');

    document.documentElement.setAttribute('data-theme', savedTheme);
    icon.textContent = savedTheme === 'dark' ? '☀️' : '🌙';
});
""";
    }
}
