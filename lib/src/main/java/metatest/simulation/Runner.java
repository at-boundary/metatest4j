package metatest.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import metatest.core.config.FaultCollection;
import metatest.core.config.SimulatorConfig;
import metatest.core.interceptor.TestContext;
import metatest.http.Request;
import metatest.http.Response;
import metatest.injection.FaultStrategy;
import metatest.injection.EmptyListStrategy;
import metatest.injection.EmptyStringStrategy;
import metatest.injection.MissingFieldStrategy;
import metatest.injection.NullFieldStrategy;
import metatest.invariant.InvariantSimulator;
import metatest.simulation.FaultSimulationReport;
import metatest.simulation.TestLevelSimulationResults;
import metatest.core.normalizer.EndpointPatternNormalizer;
import org.aspectj.lang.ProceedingJoinPoint;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class Runner {

    private static final Map<FaultCollection, FaultStrategy> FAULT_STRATEGIES;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final FaultSimulationReport REPORT = FaultSimulationReport.getInstance();
    private static final List<FaultCollection> ENABLED_FAULTS = SimulatorConfig.getEnabledFaults();

    static {
        Map<FaultCollection, FaultStrategy> strategies = new HashMap<>();
        strategies.put(FaultCollection.null_field, new NullFieldStrategy());
        strategies.put(FaultCollection.missing_field, new MissingFieldStrategy());
        strategies.put(FaultCollection.empty_list, new EmptyListStrategy());
        strategies.put(FaultCollection.empty_string, new EmptyStringStrategy());
        FAULT_STRATEGIES = Collections.unmodifiableMap(strategies);
    }

    private Runner() {}

    public static void executeTestWithSimulatedFaults(ProceedingJoinPoint joinPoint, TestContext context) throws Throwable {
        String testName = joinPoint.getSignature().getName();

        List<TestContext.RequestResponsePair> capturedRequests = context.getCapturedRequests();
        if (capturedRequests == null || capturedRequests.isEmpty()) {
            System.err.println("[METATEST-WARN] No requests were captured. Skipping fault simulation.");
            return;
        }

        System.out.printf("%n[Metatest-Sim] === Starting simulations for test: '%s' ===%n", testName);
        System.out.printf("[Metatest-Sim] Captured %d HTTP request(s)%n", capturedRequests.size());

        // Apply multiple endpoints strategy filter
        List<TestContext.RequestResponsePair> requestsToSimulate = filterRequestsByStrategy(capturedRequests);
        System.out.printf("[Metatest-Sim] Simulating %d request(s) after applying strategy%n", requestsToSimulate.size());

        // Simulate faults for filtered requests
        for (int i = 0; i < requestsToSimulate.size(); i++) {
            // Get original index in capturedRequests for proper injection
            int requestIndex = capturedRequests.indexOf(requestsToSimulate.get(i));
            TestContext.RequestResponsePair pair = capturedRequests.get(requestIndex);
            Request originalRequest = pair.getRequest();
            Response originalResponse = pair.getResponse();

            if (originalResponse == null || originalRequest == null) {
                System.err.println("[METATEST-WARN] Request/response pair #" + requestIndex + " is incomplete. Skipping.");
                continue;
            }

            String endpointPath = URI.create(originalRequest.getUrl()).getPath();
            String endpointPattern = EndpointPatternNormalizer.normalize(endpointPath);
            String requestBody = originalRequest.getBody();

            System.out.printf("%n[Metatest-Sim] --- Request #%d: '%s' (pattern: '%s') ---%n",
                    requestIndex, endpointPath, endpointPattern);

            // Check if we should simulate this response based on status code and content
            int statusCode = originalResponse.getStatusCode();
            Map<String, Object> responseMap = originalResponse.getResponseAsMap();
            String responseBody = originalResponse.getBody();

            if (!SimulatorConfig.shouldSimulateResponse(statusCode, responseMap, responseBody)) {
                System.out.printf("[Metatest-Sim] Skipping simulations for request #%d (status: %d)%n", requestIndex, statusCode);
                continue;
            }

            System.out.printf("[Metatest-Sim] Response status: %d (simulation will proceed)%n", statusCode);

            // Set which request we're currently simulating
            context.setCurrentSimulationIndex(requestIndex);

            // === Contract Faults (field-level mutations) ===
            boolean stopOnFirstCatch = context.getResolvedTestConfig() != null
                    ? context.getResolvedTestConfig().isStopOnFirstCatch()
                    : SimulatorConfig.isStopOnFirstCatchEnabled();
            List<FaultCollection> enabledFaults = context.getResolvedTestConfig() != null
                    ? context.getResolvedTestConfig().getEnabledFaults()
                    : ENABLED_FAULTS;
            for (String field : originalResponse.getResponseAsMap().keySet()) {
                for (FaultCollection fault : enabledFaults) {
                    // Skip if stop_on_first_catch is enabled and fault was already caught
                    if (stopOnFirstCatch && REPORT.isContractFaultCaught(endpointPattern, fault.name(), field)) {
                        System.out.printf("  -> Skipping fault '%s' on field '%s' (already caught by another test)%n", fault, field);
                        continue;
                    }

                    setFieldFault(context, field, fault, originalResponse, endpointPattern);

                    System.out.printf("  -> Rerunning test '%s' with fault: %s on field: '%s'%n", testName, fault, field);
                    TestLevelSimulationResults testLevelResults = new TestLevelSimulationResults();
                    testLevelResults.setTest(testName);

                    try {
                        context.resetRequestCounter(); // Reset counter before each test re-run
                        joinPoint.proceed(); // Re-run the test method
                        testLevelResults.setCaught(false);
                        System.err.printf("  [FAULT NOT DETECTED] Test '%s' passed for fault '%s' on field '%s'%n", testName, fault, field);
                    } catch (Throwable t) {
                        testLevelResults.setCaught(true);
                        testLevelResults.setError(t.getMessage());
                        System.out.printf("  [FAULT DETECTED] Test '%s' failed as expected for fault '%s' on field '%s'%n", testName, fault, field);

                        // Mark as caught if stop_on_first_catch is enabled
                        if (stopOnFirstCatch) {
                            REPORT.markContractFaultCaught(endpointPattern, fault.name(), field);
                        }
                    } finally {
                        context.clearSimulation();
                    }

                    REPORT.recordResult(endpointPattern, field, fault.name(), testLevelResults);
                }
            }

            // === Invariant Violations (business rule mutations) ===
            String httpMethod = originalRequest.getMethod();
            InvariantSimulator.simulateInvariantViolations(
                    joinPoint, context, testName, endpointPattern, httpMethod,
                    originalResponse, requestIndex);
        }

        // Reset simulation index after all requests are simulated
        context.setCurrentSimulationIndex(-1);
        System.out.printf("[Metatest-Sim] === Completed all simulations for test: '%s' ===%n%n", testName);
    }

    /**
     * Filters captured requests based on the multiple endpoints strategy configuration.
     *
     * @param capturedRequests All captured requests from the test
     * @return Filtered list of requests to simulate
     */
    private static List<TestContext.RequestResponsePair> filterRequestsByStrategy(List<TestContext.RequestResponsePair> capturedRequests) {
        SimulatorConfig.MultipleEndpointsStrategy strategy = SimulatorConfig.getMultipleEndpointsStrategy();

        List<TestContext.RequestResponsePair> filtered = new ArrayList<>();

        // Step 1: Apply test_only_last_endpoint filter
        if (strategy.test_only_last_endpoint) {
            if (!capturedRequests.isEmpty()) {
                filtered.add(capturedRequests.get(capturedRequests.size() - 1));
            }
        } else {
            filtered.addAll(capturedRequests);
        }

        // Step 2: Apply exclude_endpoints filter (regex patterns)
        if (strategy.exclude_endpoints != null && !strategy.exclude_endpoints.isEmpty()) {
            List<TestContext.RequestResponsePair> afterExclusion = new ArrayList<>();

            for (TestContext.RequestResponsePair pair : filtered) {
                String endpointPath = URI.create(pair.getRequest().getUrl()).getPath();
                String endpointPattern = EndpointPatternNormalizer.normalize(endpointPath);

                boolean excluded = false;
                for (String excludePattern : strategy.exclude_endpoints) {
                    if (endpointPattern.matches(excludePattern)) {
                        excluded = true;
                        break;
                    }
                }

                if (!excluded) {
                    afterExclusion.add(pair);
                }
            }

            return afterExclusion;
        }

        return filtered;
    }

    /**
     * Creates a simulated faulty response and sets it on the current TestContext.
     */
    private static void setFieldFault(TestContext context, String field, FaultCollection fault, Response originalResponse, String endpointPattern) {
        if (originalResponse == null) {
            throw new IllegalStateException("Original response is null. Cannot create fault.");
        }

        FaultStrategy strategy = FAULT_STRATEGIES.get(fault);
        if (strategy == null) {
            return;
        }

        try {
            Map<String, Object> responseMap = new HashMap<>(originalResponse.getResponseAsMap());
            String originalBody = originalResponse.getBody();
            
            strategy.apply(responseMap, field);
            String faultyBody = OBJECT_MAPPER.writeValueAsString(responseMap);

            System.out.printf("    [FAULT-INJECTION] Original response body: %s%n", originalBody);
            System.out.printf("    [FAULT-INJECTION] Simulated response body: %s%n", faultyBody);

            Response simulatedResponse = originalResponse.withBody(faultyBody);
            context.setSimulatedResponse(simulatedResponse);

        } catch (IOException e) {
            System.err.println("Failed to create simulated response body for fault " + fault + ". Error: " + e.getMessage());
        }
    }
}