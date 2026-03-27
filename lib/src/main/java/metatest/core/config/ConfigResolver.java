package metatest.core.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Merges global SimulatorConfig with a per-test-class TestScopedConfig and optional
 * per-method TestMethodConfig into a single ResolvedTestConfig for use during simulation.
 *
 * Merging rules:
 *   - invariants:  additive  (global + class-level + method-level all contribute)
 *   - faults:      override  (most specific level wins per fault type)
 *   - settings:    override  (most specific level wins per field)
 *   - exclusions:  additive  (union of all levels)
 *
 * Method matching order: exact name → wildcard pattern (most specific wildcard wins)
 */
public class ConfigResolver {

    /**
     * Resolves the effective configuration for a specific test method execution.
     *
     * @param classConfig Optional class-level config from .metatest.yml (empty if no file found)
     * @param methodName  The @Test method name being executed
     * @return Fully resolved, immutable config ready for the simulation pipeline
     */
    public static ResolvedTestConfig resolve(Optional<TestScopedConfig> classConfig, String methodName) {
        if (classConfig.isEmpty()) {
            return buildFromGlobalOnly();
        }

        TestScopedConfig scopedConfig = classConfig.get();
        TestMethodConfig methodConfig = findMethodConfig(scopedConfig, methodName);

        // Short-circuit: test is explicitly excluded from simulation
        if (methodConfig != null && methodConfig.isExclude()) {
            return ResolvedTestConfig.SKIP;
        }

        boolean stopOnFirstCatch = resolveStopOnFirstCatch(scopedConfig, methodConfig);
        String defaultQuantifier = resolveDefaultQuantifier(scopedConfig, methodConfig);
        List<FaultCollection> enabledFaults = resolveEnabledFaults(scopedConfig, methodConfig);
        Map<String, List<InvariantConfig>> mergedInvariants = mergeInvariants(scopedConfig, methodConfig);
        List<Pattern> excludedPatterns = mergeExcludedEndpointPatterns(scopedConfig, methodConfig);

        return new ResolvedTestConfig(
                false,
                stopOnFirstCatch,
                defaultQuantifier,
                enabledFaults,
                mergedInvariants,
                excludedPatterns
        );
    }

    // ── Method config lookup ──────────────────────────────────────────────────

    /**
     * Finds the best-matching TestMethodConfig for the given method name.
     * Precedence: exact match > most-specific wildcard match (fewest wildcards).
     */
    static TestMethodConfig findMethodConfig(TestScopedConfig scopedConfig, String methodName) {
        if (scopedConfig.tests == null || scopedConfig.tests.isEmpty()) {
            return null;
        }

        // 1. Exact match
        TestMethodConfig exact = scopedConfig.tests.get(methodName);
        if (exact != null) {
            return exact;
        }

        // 2. Wildcard match — pick the most specific (fewest '*' chars)
        TestMethodConfig bestMatch = null;
        int bestWildcardCount = Integer.MAX_VALUE;

        for (Map.Entry<String, TestMethodConfig> entry : scopedConfig.tests.entrySet()) {
            String pattern = entry.getKey();
            if (!pattern.contains("*") && !pattern.contains("?")) {
                continue; // exact-only patterns were already checked
            }
            if (globMatches(pattern, methodName)) {
                int wildcardCount = countWildcards(pattern);
                if (wildcardCount < bestWildcardCount) {
                    bestWildcardCount = wildcardCount;
                    bestMatch = entry.getValue();
                }
            }
        }

        return bestMatch;
    }

    private static boolean globMatches(String pattern, String input) {
        String regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        return input.matches(regex);
    }

    private static int countWildcards(String pattern) {
        int count = 0;
        for (char c : pattern.toCharArray()) {
            if (c == '*' || c == '?') count++;
        }
        return count;
    }

    // ── Settings resolution ───────────────────────────────────────────────────

    private static boolean resolveStopOnFirstCatch(TestScopedConfig classConfig, TestMethodConfig methodConfig) {
        // Method-level
        if (methodConfig != null && methodConfig.settings != null) {
            return methodConfig.settings.stop_on_first_catch;
        }
        // Class-level
        if (classConfig.settings != null) {
            return classConfig.settings.stop_on_first_catch;
        }
        // Global fallback
        return SimulatorConfig.isStopOnFirstCatchEnabled();
    }

    private static String resolveDefaultQuantifier(TestScopedConfig classConfig, TestMethodConfig methodConfig) {
        // Method-level
        if (methodConfig != null && methodConfig.settings != null
                && methodConfig.settings.default_quantifier != null) {
            return methodConfig.settings.default_quantifier;
        }
        // Class-level
        if (classConfig.settings != null && classConfig.settings.default_quantifier != null) {
            return classConfig.settings.default_quantifier;
        }
        // Global fallback
        return SimulatorConfig.getDefaultQuantifier();
    }

    // ── Fault resolution ──────────────────────────────────────────────────────

    /**
     * Resolves the enabled fault list.
     * For each FaultCollection value: method-level override → class-level override → global list.
     */
    private static List<FaultCollection> resolveEnabledFaults(
            TestScopedConfig classConfig, TestMethodConfig methodConfig) {

        List<FaultCollection> globalFaults = SimulatorConfig.getEnabledFaults();
        List<FaultCollection> result = new ArrayList<>();

        for (FaultCollection fault : FaultCollection.values()) {
            Boolean enabled = null;

            // Method-level fault override (most specific)
            if (methodConfig != null && methodConfig.faults != null) {
                enabled = methodConfig.faults.getEnabled(fault);
            }
            // Class-level fault override
            if (enabled == null && classConfig.faults != null) {
                enabled = classConfig.faults.getEnabled(fault);
            }
            // Fall back to global enabled faults
            if (enabled == null) {
                enabled = globalFaults.contains(fault);
            }

            if (Boolean.TRUE.equals(enabled)) {
                result.add(fault);
            }
        }

        return result;
    }

    // ── Invariant merging ─────────────────────────────────────────────────────

    /**
     * Builds the additive map of invariants:
     *   global config.yml  +  class-level .metatest.yml  +  method-level override
     *
     * All three levels contribute; duplicates (same invariant name) are NOT deduplicated
     * to keep the merge logic simple and predictable.
     */
    private static Map<String, List<InvariantConfig>> mergeInvariants(
            TestScopedConfig classConfig, TestMethodConfig methodConfig) {

        Map<String, List<InvariantConfig>> result = new HashMap<>();

        // 1. Global invariants from config.yml
        Map<String, Map<String, MethodInvariantsConfig>> globalEndpoints = SimulatorConfig.getAllEndpointInvariants();
        addEndpointInvariants(globalEndpoints, result);

        // 2. Class-level invariants from .metatest.yml
        if (classConfig.endpoints != null) {
            addEndpointInvariants(classConfig.endpoints, result);
        }

        // 3. Method-level invariants (most specific, added last)
        if (methodConfig != null && methodConfig.endpoints != null) {
            addEndpointInvariants(methodConfig.endpoints, result);
        }

        return result;
    }

    private static void addEndpointInvariants(
            Map<String, Map<String, MethodInvariantsConfig>> source,
            Map<String, List<InvariantConfig>> target) {

        if (source == null) return;

        for (Map.Entry<String, Map<String, MethodInvariantsConfig>> endpointEntry : source.entrySet()) {
            String endpointPattern = endpointEntry.getKey();
            Map<String, MethodInvariantsConfig> methodMap = endpointEntry.getValue();
            if (methodMap == null) continue;

            for (Map.Entry<String, MethodInvariantsConfig> methodEntry : methodMap.entrySet()) {
                String method = methodEntry.getKey().toUpperCase();
                MethodInvariantsConfig mic = methodEntry.getValue();
                if (mic == null || mic.getInvariants() == null || mic.getInvariants().isEmpty()) continue;

                String key = endpointPattern + "::" + method;
                target.computeIfAbsent(key, k -> new ArrayList<>()).addAll(mic.getInvariants());
            }
        }
    }

    // ── Exclusion merging ─────────────────────────────────────────────────────

    private static List<Pattern> mergeExcludedEndpointPatterns(
            TestScopedConfig classConfig, TestMethodConfig methodConfig) {

        List<String> patterns = new ArrayList<>();

        if (classConfig.exclusions != null && classConfig.exclusions.endpoints != null) {
            patterns.addAll(classConfig.exclusions.endpoints);
        }
        if (methodConfig != null && methodConfig.exclusions != null
                && methodConfig.exclusions.endpoints != null) {
            patterns.addAll(methodConfig.exclusions.endpoints);
        }

        return compileGlobPatterns(patterns);
    }

    private static List<Pattern> compileGlobPatterns(List<String> globs) {
        List<Pattern> compiled = new ArrayList<>();
        for (String glob : globs) {
            String regex = glob.replace(".", "\\.").replace("*", ".*").replace("?", ".");
            compiled.add(Pattern.compile(regex));
        }
        return compiled;
    }

    // ── Global-only fallback ──────────────────────────────────────────────────

    /**
     * Wraps global config values in a ResolvedTestConfig when no .metatest.yml is present.
     * Invariants come directly from SimulatorConfig; no exclusions are added.
     */
    private static ResolvedTestConfig buildFromGlobalOnly() {
        Map<String, List<InvariantConfig>> invariants = new HashMap<>();
        addEndpointInvariants(SimulatorConfig.getAllEndpointInvariants(), invariants);

        return new ResolvedTestConfig(
                false,
                SimulatorConfig.isStopOnFirstCatchEnabled(),
                SimulatorConfig.getDefaultQuantifier(),
                SimulatorConfig.getEnabledFaults(),
                invariants,
                List.of()
        );
    }
}
