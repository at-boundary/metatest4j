package metatest.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import metatest.api.FaultStrategyApiClient;
import metatest.api.dto.SubmitSimulationResultsRequest;
import metatest.report.HtmlReportGenerator;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FaultSimulationReport {
    private static final FaultSimulationReport INSTANCE = new FaultSimulationReport();
    private static final String DEFAULT_REPORT_PATH = "fault_simulation_report.json";

    private final Map<String, EndpointFaultResults> report = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final FaultStrategyApiClient apiClient;
    private LocalDateTime executionStartTime;

    /**
     * Tracks faults that have been caught by at least one test.
     * Key format: "endpoint|contract|faultType|field" or "endpoint|invariant|invariantName"
     * Used when stop_on_first_catch is enabled to skip redundant simulations.
     */
    private final Set<String> caughtFaults = ConcurrentHashMap.newKeySet();

    private FaultSimulationReport() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.apiClient = new FaultStrategyApiClient();
        this.executionStartTime = LocalDateTime.now(); // Set when first result is recorded
    }

    public static FaultSimulationReport getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the internal report map for direct access.
     */
    public Map<String, EndpointFaultResults> getReport() {
        return report;
    }

    /**
     * Records a contract fault result (field-level mutation like null_field, missing_field).
     */
    public void recordResult(String endpoint, String field, String faultType, TestLevelSimulationResults result) {
        if (endpoint == null || field == null || faultType == null || result == null) {
            System.err.println("[METATEST-WARN] Attempted to record a contract fault result with null data. Skipping.");
            return;
        }

        report.computeIfAbsent(endpoint, k -> new EndpointFaultResults())
                .recordContractFault(faultType, field, result);
    }

    /**
     * Records a invariant fault result (business rule violation).
     */
    public void recordInvariantResult(String endpoint, String invariantName, TestLevelSimulationResults result) {
        if (endpoint == null || invariantName == null || result == null) {
            System.err.println("[METATEST-WARN] Attempted to record a invariant fault result with null data. Skipping.");
            return;
        }

        report.computeIfAbsent(endpoint, k -> new EndpointFaultResults())
                .recordInvariantFault(invariantName, result);
    }

    // ==================== stop_on_first_catch tracking ====================

    /**
     * Checks if a contract fault has already been caught by any test.
     *
     * @param endpoint  The API endpoint
     * @param faultType The fault type (e.g., "null_field", "missing_field")
     * @param field     The field name
     * @return true if the fault has been caught, false otherwise
     */
    public boolean isContractFaultCaught(String endpoint, String faultType, String field) {
        String key = buildContractFaultKey(endpoint, faultType, field);
        return caughtFaults.contains(key);
    }

    /**
     * Marks a contract fault as caught.
     *
     * @param endpoint  The API endpoint
     * @param faultType The fault type
     * @param field     The field name
     */
    public void markContractFaultCaught(String endpoint, String faultType, String field) {
        String key = buildContractFaultKey(endpoint, faultType, field);
        caughtFaults.add(key);
    }

    /**
     * Checks if an invariant fault has already been caught by any test.
     *
     * @param endpoint      The API endpoint
     * @param invariantName The invariant name
     * @return true if the fault has been caught, false otherwise
     */
    public boolean isInvariantFaultCaught(String endpoint, String invariantName) {
        String key = buildInvariantFaultKey(endpoint, invariantName);
        return caughtFaults.contains(key);
    }

    /**
     * Marks an invariant fault as caught.
     *
     * @param endpoint      The API endpoint
     * @param invariantName The invariant name
     */
    public void markInvariantFaultCaught(String endpoint, String invariantName) {
        String key = buildInvariantFaultKey(endpoint, invariantName);
        caughtFaults.add(key);
    }

    /**
     * Clears the caught faults tracking. Useful for test isolation.
     */
    public void clearCaughtFaultsTracking() {
        caughtFaults.clear();
    }

    /**
     * Prints a per-test summary to stdout after all simulations complete.
     * Shows overall detection rate and, for each test, caught vs escaped fault counts.
     * Tests with escaped faults are listed first; escaped fault descriptions follow each test.
     */
    public void printConsoleSummary() {
        Map<String, int[]> perTestStats = new LinkedHashMap<>();  // testName -> [caught, total]
        Map<String, List<String>> perTestEscaped = new LinkedHashMap<>();
        int globalTotal = 0;
        int globalCaught = 0;

        for (Map.Entry<String, EndpointFaultResults> epEntry : report.entrySet()) {
            String endpoint = epEntry.getKey();
            EndpointFaultResults results = epEntry.getValue();

            for (Map.Entry<String, Map<String, FaultSimulationResult>> ftEntry
                    : results.getContractFaults().entrySet()) {
                String faultType = ftEntry.getKey();
                for (Map.Entry<String, FaultSimulationResult> fieldEntry
                        : ftEntry.getValue().entrySet()) {
                    globalTotal++;
                    if (fieldEntry.getValue().isCaughtByAnyTest()) globalCaught++;
                    String desc = endpoint + " [" + faultType + ":" + fieldEntry.getKey() + "]";
                    accumulatePerTest(fieldEntry.getValue(), desc, perTestStats, perTestEscaped);
                }
            }

            for (Map.Entry<String, FaultSimulationResult> invEntry
                    : results.getInvariantFaults().entrySet()) {
                globalTotal++;
                if (invEntry.getValue().isCaughtByAnyTest()) globalCaught++;
                String desc = endpoint + " [invariant:" + invEntry.getKey() + "]";
                accumulatePerTest(invEntry.getValue(), desc, perTestStats, perTestEscaped);
            }
        }

        if (globalTotal == 0) return;

        double rate = globalCaught * 100.0 / globalTotal;
        String sep = "=".repeat(70);
        String div = "-".repeat(70);

        System.out.println();
        System.out.println(sep);
        System.out.println(" Metatest -- Simulation Run Summary");
        System.out.println(sep);
        System.out.printf(" Overall: %d total  |  %d detected (%.0f%%)  |  %d escaped%n",
                globalTotal, globalCaught, rate, globalTotal - globalCaught);

        if (!perTestStats.isEmpty()) {
            List<Map.Entry<String, int[]>> sorted = new ArrayList<>(perTestStats.entrySet());
            sorted.sort((a, b) -> Integer.compare(
                    b.getValue()[1] - b.getValue()[0],
                    a.getValue()[1] - a.getValue()[0]));

            System.out.println(div);
            System.out.printf(" %-36s  %6s  %5s  %7s%n", "Test", "Caught", "Total", "Escaped");
            System.out.println(div);
            for (Map.Entry<String, int[]> entry : sorted) {
                int caught  = entry.getValue()[0];
                int total   = entry.getValue()[1];
                int escaped = total - caught;
                System.out.printf(" %-36s  %6d  %5d  %7d%n",
                        entry.getKey(), caught, total, escaped);
                if (escaped > 0) {
                    List<String> ef = perTestEscaped.get(entry.getKey());
                    if (ef != null) {
                        for (String fault : ef) System.out.println("   [X] " + fault);
                    }
                }
            }
        }

        System.out.println(sep);
        System.out.println();
    }

    private void accumulatePerTest(FaultSimulationResult result, String faultDesc,
            Map<String, int[]> perTestStats, Map<String, List<String>> perTestEscaped) {
        Set<String> caughtTests = new HashSet<>();
        for (TestLevelSimulationResults ts : result.getCaughtBy()) {
            caughtTests.add(ts.getTest());
        }
        for (String testName : result.getTestedBy()) {
            perTestStats.computeIfAbsent(testName, k -> new int[]{0, 0});
            perTestEscaped.computeIfAbsent(testName, k -> new ArrayList<>());
            perTestStats.get(testName)[1]++;
            if (caughtTests.contains(testName)) {
                perTestStats.get(testName)[0]++;
            } else {
                perTestEscaped.get(testName).add(faultDesc);
            }
        }
    }

    private String buildContractFaultKey(String endpoint, String faultType, String field) {
        return endpoint + "|contract|" + faultType + "|" + field;
    }

    private String buildInvariantFaultKey(String endpoint, String invariantName) {
        return endpoint + "|invariant|" + invariantName;
    }

    // ======================================================================

    public void sendResultsToAPI() {
        try {
            SubmitSimulationResultsRequest request = convertToApiRequest();
            
            apiClient.submitSimulationResults(request);
            
            System.out.println("Successfully sent simulation results to API");
            
        } catch (Exception e) {
            System.err.println("Failed to send results to API: " + e.getMessage());
            e.printStackTrace();
            
            System.out.println("Falling back to JSON file...");
            createJSONReport();
        }
    }
    
    public void createJSONReport() {
        try {
            File reportFile = new File(DEFAULT_REPORT_PATH);
            if (reportFile.getParentFile() != null) {
                reportFile.getParentFile().mkdirs();
            }
            objectMapper.writeValue(reportFile, report);
            System.out.println("Saving fault simulation report to JSON file: " + reportFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save report: " + e.getMessage());
        }
    }
    
    private SubmitSimulationResultsRequest convertToApiRequest() {
        SubmitSimulationResultsRequest request = new SubmitSimulationResultsRequest();

        request.setTestSuiteName(extractTestSuiteName());
        request.setStrategyId(getStrategyIdFromAPI()); // Get the strategy ID that was used
        request.setExecutionStartTime(executionStartTime);
        request.setExecutionEndTime(LocalDateTime.now());
        request.setAgentVersion("1.0.0-dev");

        Map<String, SubmitSimulationResultsRequest.EndpointResults> convertedResults = new HashMap<>();

        for (Map.Entry<String, EndpointFaultResults> endpointEntry : report.entrySet()) {
            String endpoint = endpointEntry.getKey();
            EndpointFaultResults endpointFaultResults = endpointEntry.getValue();

            SubmitSimulationResultsRequest.EndpointResults endpointResults = new SubmitSimulationResultsRequest.EndpointResults();
            Map<String, SubmitSimulationResultsRequest.FieldResults> fieldResultsMap = new HashMap<>();

            // Process contract faults: faultType -> field -> result
            Map<String, Map<String, FaultSimulationResult>> contractFaults = endpointFaultResults.getContractFaults();

            // Invert the structure: we need field -> faultType -> result for the API
            Map<String, Map<String, FaultSimulationResult>> fieldToFaultType = new HashMap<>();
            for (Map.Entry<String, Map<String, FaultSimulationResult>> faultTypeEntry : contractFaults.entrySet()) {
                String faultType = faultTypeEntry.getKey();
                for (Map.Entry<String, FaultSimulationResult> fieldEntry : faultTypeEntry.getValue().entrySet()) {
                    String fieldName = fieldEntry.getKey();
                    fieldToFaultType
                            .computeIfAbsent(fieldName, k -> new HashMap<>())
                            .put(faultType, fieldEntry.getValue());
                }
            }

            for (Map.Entry<String, Map<String, FaultSimulationResult>> fieldEntry : fieldToFaultType.entrySet()) {
                String fieldName = fieldEntry.getKey();
                Map<String, FaultSimulationResult> faultTypesData = fieldEntry.getValue();

                SubmitSimulationResultsRequest.FieldResults fieldResults = new SubmitSimulationResultsRequest.FieldResults();

                for (Map.Entry<String, FaultSimulationResult> faultTypeEntry : faultTypesData.entrySet()) {
                    String faultType = faultTypeEntry.getKey();
                    FaultSimulationResult faultSimResult = faultTypeEntry.getValue();

                    SubmitSimulationResultsRequest.FaultTypeResult faultTypeResult = convertFaultTypeResult(faultSimResult.getCaughtBy());

                    switch (faultType) {
                        case "null_field":
                            fieldResults.setNullField(faultTypeResult);
                            break;
                        case "missing_field":
                            fieldResults.setMissingField(faultTypeResult);
                            break;
                        case "empty_string":
                            fieldResults.setEmptyString(faultTypeResult);
                            break;
                        case "empty_list":
                            fieldResults.setEmptyList(faultTypeResult);
                            break;
                        case "invalid_value":
                            fieldResults.setInvalidValue(faultTypeResult);
                            break;
                    }
                }

                fieldResultsMap.put(fieldName, fieldResults);
            }

            endpointResults.setFields(fieldResultsMap);
            convertedResults.put(endpoint, endpointResults);
        }

        request.setResults(convertedResults);

        SubmitSimulationResultsRequest.SimulationSummary summary = calculateSummary(convertedResults);
        request.setSummary(summary);

        return request;
    }
    
    private SubmitSimulationResultsRequest.FaultTypeResult convertFaultTypeResult(List<TestLevelSimulationResults> testResults) {
        SubmitSimulationResultsRequest.FaultTypeResult faultTypeResult = new SubmitSimulationResultsRequest.FaultTypeResult();
        
        List<SubmitSimulationResultsRequest.TestResult> convertedTestResults = new ArrayList<>();
        boolean caughtByAny = false;
        
        for (TestLevelSimulationResults testResult : testResults) {
            SubmitSimulationResultsRequest.TestResult apiTestResult = new SubmitSimulationResultsRequest.TestResult();
            apiTestResult.setTest(testResult.getTest());
            apiTestResult.setCaught(testResult.isCaught());
            apiTestResult.setError(testResult.getError());
            
            if (testResult.isCaught()) {
                caughtByAny = true;
            }
            
            convertedTestResults.add(apiTestResult);
        }
        
        faultTypeResult.setCaughtByAny(caughtByAny);
        faultTypeResult.setResults(convertedTestResults);
        
        return faultTypeResult;
    }
    
    private SubmitSimulationResultsRequest.SimulationSummary calculateSummary(Map<String, SubmitSimulationResultsRequest.EndpointResults> results) {
        int totalFaultTypes = 0;
        int faultTypesCaught = 0;
        int totalTestExecutions = 0;
        int successfulTestExecutions = 0;
        
        for (SubmitSimulationResultsRequest.EndpointResults endpointResults : results.values()) {
            for (SubmitSimulationResultsRequest.FieldResults fieldResults : endpointResults.getFields().values()) {
                
                if (fieldResults.getNullField() != null) {
                    totalFaultTypes++;
                    if (fieldResults.getNullField().getCaughtByAny()) {
                        faultTypesCaught++;
                    }
                    totalTestExecutions += fieldResults.getNullField().getResults().size();
                    successfulTestExecutions += (int) fieldResults.getNullField().getResults().stream()
                            .mapToInt(result -> result.getCaught() ? 1 : 0).sum();
                }
                
                if (fieldResults.getMissingField() != null) {
                    totalFaultTypes++;
                    if (fieldResults.getMissingField().getCaughtByAny()) {
                        faultTypesCaught++;
                    }
                    totalTestExecutions += fieldResults.getMissingField().getResults().size();
                    successfulTestExecutions += (int) fieldResults.getMissingField().getResults().stream()
                            .mapToInt(result -> result.getCaught() ? 1 : 0).sum();
                }
                
                if (fieldResults.getEmptyString() != null) {
                    totalFaultTypes++;
                    if (fieldResults.getEmptyString().getCaughtByAny()) {
                        faultTypesCaught++;
                    }
                    totalTestExecutions += fieldResults.getEmptyString().getResults().size();
                    successfulTestExecutions += (int) fieldResults.getEmptyString().getResults().stream()
                            .mapToInt(result -> result.getCaught() ? 1 : 0).sum();
                }
                
                if (fieldResults.getEmptyList() != null) {
                    totalFaultTypes++;
                    if (fieldResults.getEmptyList().getCaughtByAny()) {
                        faultTypesCaught++;
                    }
                    totalTestExecutions += fieldResults.getEmptyList().getResults().size();
                    successfulTestExecutions += (int) fieldResults.getEmptyList().getResults().stream()
                            .mapToInt(result -> result.getCaught() ? 1 : 0).sum();
                }
                
                if (fieldResults.getInvalidValue() != null) {
                    totalFaultTypes++;
                    if (fieldResults.getInvalidValue().getCaughtByAny()) {
                        faultTypesCaught++;
                    }
                    totalTestExecutions += fieldResults.getInvalidValue().getResults().size();
                    successfulTestExecutions += (int) fieldResults.getInvalidValue().getResults().stream()
                            .mapToInt(result -> result.getCaught() ? 1 : 0).sum();
                }
            }
        }
        
        SubmitSimulationResultsRequest.SimulationSummary summary = new SubmitSimulationResultsRequest.SimulationSummary();
        summary.setTotalFaultTypes(totalFaultTypes);
        summary.setFaultTypesCaught(faultTypesCaught);
        summary.setFaultTypesMissed(totalFaultTypes - faultTypesCaught);
        summary.setFaultCoverageScore(totalFaultTypes > 0 ? (double) faultTypesCaught / totalFaultTypes : 0.0);
        summary.setTotalTestExecutions(totalTestExecutions);
        summary.setTestExecutionSuccessRate(totalTestExecutions > 0 ? (double) successfulTestExecutions / totalTestExecutions : 0.0);
        
        return summary;
    }
    
    private String extractTestSuiteName() {
        for (EndpointFaultResults endpointData : report.values()) {
            // Check contract faults
            for (Map<String, FaultSimulationResult> fieldData : endpointData.getContractFaults().values()) {
                for (FaultSimulationResult faultResult : fieldData.values()) {
                    if (!faultResult.isEmpty() && !faultResult.getTestedBy().isEmpty()) {
                        String fullTestName = faultResult.getTestedBy().get(0);
                        // Extract class name from method name (e.g., "testCreatePayment" -> "PaymentTest")
                        return fullTestName.replaceAll("test.*", "") + "Test";
                    }
                }
            }
            // Check invariant faults
            for (FaultSimulationResult faultResult : endpointData.getInvariantFaults().values()) {
                if (!faultResult.isEmpty() && !faultResult.getTestedBy().isEmpty()) {
                    String fullTestName = faultResult.getTestedBy().get(0);
                    return fullTestName.replaceAll("test.*", "") + "Test";
                }
            }
        }
        return "UnknownTestSuite";
    }
    
    private UUID getStrategyIdFromAPI() {

        try {
            var response = apiClient.getContractFaultStrategies();
            return response.getStrategies().stream()
                    .filter(s -> s.getEnabled())
                    .findFirst()
                    .map(s -> s.getId())
                    .orElseThrow(() -> new RuntimeException("No enabled strategy found"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get strategy ID", e);
        }
    }
}