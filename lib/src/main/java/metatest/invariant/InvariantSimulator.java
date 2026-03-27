package metatest.invariant;

import com.fasterxml.jackson.databind.ObjectMapper;
import metatest.core.config.InvariantConfig;
import metatest.core.config.SimulatorConfig;
import metatest.core.interceptor.TestContext;
import metatest.http.Response;
import metatest.simulation.FaultSimulationReport;
import metatest.simulation.TestLevelSimulationResults;
import org.aspectj.lang.ProceedingJoinPoint;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes invariant-based fault simulation.
 * For each configured invariant rule, generates mutations that violate the rule
 * and verifies if tests catch these violations.
 */
public class InvariantSimulator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final FaultSimulationReport REPORT = FaultSimulationReport.getInstance();
    private static final ViolationGenerator VIOLATION_GENERATOR = new ViolationGenerator();
    private static final ConditionEvaluator CONDITION_EVALUATOR = new ConditionEvaluator();

    /**
     * Executes invariant-based fault simulation for a specific endpoint response.
     *
     * @param joinPoint The test method join point
     * @param context The test context
     * @param testName The name of the test
     * @param endpointPattern The normalized endpoint pattern
     * @param httpMethod The HTTP method (GET, POST, etc.)
     * @param originalResponse The original response
     * @param requestIndex The index of this request in the captured requests
     */
    public static void simulateInvariantViolations(
            ProceedingJoinPoint joinPoint,
            TestContext context,
            String testName,
            String endpointPattern,
            String httpMethod,
            Response originalResponse,
            int requestIndex) {

        // Get invariants: prefer resolved test config (merges global + class + method), fall back to global
        List<InvariantConfig> invariants = (context.getResolvedTestConfig() != null)
                ? context.getResolvedTestConfig().getInvariantsFor(endpointPattern, httpMethod)
                : SimulatorConfig.getInvariantsForEndpoint(endpointPattern, httpMethod);

        if (invariants.isEmpty()) {
            System.out.printf("[Metatest-Invariant] No invariants configured for %s %s%n", httpMethod, endpointPattern);
            return;
        }

        System.out.printf("[Metatest-Invariant] Found %d invariant(s) for %s %s%n",
                invariants.size(), httpMethod, endpointPattern);

        Map<String, Object> responseMap = originalResponse.getResponseAsMap();
        boolean stopOnFirstCatch = (context.getResolvedTestConfig() != null)
                ? context.getResolvedTestConfig().isStopOnFirstCatch()
                : SimulatorConfig.isStopOnFirstCatchEnabled();

        for (InvariantConfig invariant : invariants) {
            String invariantName = invariant.getName() != null ? invariant.getName() : "unnamed_invariant";

            // Skip if stop_on_first_catch is enabled and invariant fault was already caught
            if (stopOnFirstCatch && REPORT.isInvariantFaultCaught(endpointPattern, invariantName)) {
                System.out.printf("  -> Skipping invariant '%s' (already caught by another test)%n", invariantName);
                continue;
            }

            // First, verify the original response satisfies the invariant
            ConditionEvaluator.EvaluationResult originalResult =
                    CONDITION_EVALUATOR.evaluate(invariant, responseMap);

            if (!originalResult.isSatisfied()) {
                System.out.printf("  [WARN] Original response already violates invariant '%s': %s%n",
                        invariantName, originalResult.getMessage());
                // Record that the original already violates
                // Skip simulation for this invariant
                continue;
            }

            // Generate violations for this invariant
            List<Mutation> mutations = VIOLATION_GENERATOR.generateViolations(invariant, responseMap);

            if (mutations.isEmpty()) {
                System.out.printf("  [INFO] No mutations generated for invariant '%s' (may be conditional with unmet precondition)%n",
                        invariantName);
                continue;
            }

            System.out.printf("  [INFO] Testing %d mutation(s) for invariant '%s'%n",
                    mutations.size(), invariantName);

            // Execute each mutation
            for (Mutation mutation : mutations) {
                executeMutation(joinPoint, context, testName, endpointPattern,
                        originalResponse, requestIndex, invariant, mutation, stopOnFirstCatch);
            }
        }
    }

    /**
     * Executes a single mutation and records the result.
     */
    private static void executeMutation(
            ProceedingJoinPoint joinPoint,
            TestContext context,
            String testName,
            String endpointPattern,
            Response originalResponse,
            int requestIndex,
            InvariantConfig invariant,
            Mutation mutation,
            boolean stopOnFirstCatch) {

        String invariantName = invariant.getName() != null ? invariant.getName() : "unnamed";
        String field = mutation.getField();

        try {
            // Apply mutation to response
            Map<String, Object> mutatedMap = new HashMap<>(originalResponse.getResponseAsMap());
            applyMutation(mutatedMap, mutation);

            String mutatedBody = OBJECT_MAPPER.writeValueAsString(mutatedMap);

            System.out.printf("    -> Testing mutation: %s%n", mutation.getDescription());
            System.out.printf("       Field: %s, Value: %s%n", field, mutation.getValue());

            // Create simulated response with mutation
            Response simulatedResponse = originalResponse.withBody(mutatedBody);
            context.setSimulatedResponse(simulatedResponse);
            context.setCurrentSimulationIndex(requestIndex);

            // Create result object
            TestLevelSimulationResults testLevelResults = new TestLevelSimulationResults();
            testLevelResults.setTest(testName);

            try {
                context.resetRequestCounter();
                joinPoint.proceed(); // Re-run the test

                // Test passed - fault not detected
                testLevelResults.setCaught(false);
                System.err.printf("    [INVARIANT NOT VALIDATED] Test '%s' passed for violation of '%s' on field '%s'%n",
                        testName, invariantName, field);

            } catch (Throwable t) {
                // Test failed - fault detected
                testLevelResults.setCaught(true);
                testLevelResults.setError(t.getMessage());
                System.out.printf("    [INVARIANT VALIDATED] Test '%s' failed as expected for violation of '%s' on field '%s'%n",
                        testName, invariantName, field);

                // Mark as caught if stop_on_first_catch is enabled
                if (stopOnFirstCatch) {
                    REPORT.markInvariantFaultCaught(endpointPattern, invariantName);
                }

            } finally {
                context.clearSimulation();
            }

            // Record result with invariant name
            REPORT.recordInvariantResult(endpointPattern, invariantName, testLevelResults);

        } catch (IOException e) {
            System.err.printf("    [ERROR] Failed to apply mutation for invariant '%s': %s%n",
                    invariantName, e.getMessage());
        }
    }

    /**
     * Applies a mutation to a response map.
     */
    @SuppressWarnings("unchecked")
    private static void applyMutation(Map<String, Object> responseMap, Mutation mutation) {
        String field = mutation.getField();

        // Handle nested fields
        String[] parts = field.split("\\.");
        Map<String, Object> current = responseMap;

        // Navigate to parent of target field
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                // Create nested map if it doesn't exist
                Map<String, Object> newMap = new HashMap<>();
                current.put(parts[i], newMap);
                current = newMap;
            }
        }

        // Apply mutation to target field
        String targetField = parts[parts.length - 1];

        switch (mutation.getType()) {
            case SET_NULL:
                current.put(targetField, null);
                break;
            case SET_VALUE:
            case SET_EMPTY_STRING:
            case SET_EMPTY_LIST:
                current.put(targetField, mutation.getValue());
                break;
            case REMOVE_FIELD:
                current.remove(targetField);
                break;
        }
    }

    /**
     * Checks if invariants are configured for an endpoint.
     */
    public static boolean hasInvariants(String endpointPattern, String httpMethod) {
        return SimulatorConfig.hasInvariants(endpointPattern, httpMethod);
    }
}
