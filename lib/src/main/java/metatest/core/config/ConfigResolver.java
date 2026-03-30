package metatest.core.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Merges all configuration sources into a single ResolvedTestConfig for simulation.
 *
 * Merge order (all additive for invariants, most-specific-wins for everything else):
 *   1. global config.yml
 *   2. features/*.yml        (invariants only — additive)
 *   3. ClassName.metatest.yml class-level
 *   4. ClassName.metatest.yml method-level  (most specific)
 *
 * Merging rules:
 *   - invariants:  additive  (all levels contribute)
 *   - contract:    override  (most specific level wins per fault type)
 *   - settings:    override  (most specific level wins per field)
 *   - exclusions:  additive  (union of all levels)
 */
public class ConfigResolver {

    /**
     * Resolves the effective configuration for a specific test method execution.
     *
     * @param testClass   The test class being intercepted (used for feature lookup)
     * @param classConfig Optional class-level config from ClassName.metatest.yml
     * @param methodName  The @Test method name being executed
     * @return Fully resolved, immutable config ready for the simulation pipeline
     */
    public static ResolvedTestConfig resolve(
            Class<?> testClass,
            Optional<TestScopedConfig> classConfig,
            String methodName) {

        // Load features that apply to this test (may be empty)
        List<FeatureConfig> features = FeatureConfigCache.getInstance().getFeaturesFor(testClass, methodName);

        TestScopedConfig scopedConfig = classConfig.orElse(null);
        TestMethodConfig methodConfig = scopedConfig != null ? findMethodConfig(scopedConfig, methodName) : null;

        // Short-circuit: test is explicitly excluded from simulation
        if (methodConfig != null && methodConfig.isExclude()) {
            return ResolvedTestConfig.SKIP;
        }

        boolean stopOnFirstCatch = resolveStopOnFirstCatch(scopedConfig, methodConfig);
        String defaultQuantifier = resolveDefaultQuantifier(scopedConfig, methodConfig);
        List<FaultCollection> enabledFaults = resolveEnabledFaults(scopedConfig, methodConfig);
        Map<String, List<InvariantConfig>> mergedInvariants = mergeInvariants(features, scopedConfig, methodConfig);
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
                continue;
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
        if (methodConfig != null && methodConfig.settings != null) {
            return methodConfig.settings.stop_on_first_catch;
        }
        if (classConfig != null && classConfig.settings != null) {
            return classConfig.settings.stop_on_first_catch;
        }
        return SimulatorConfig.isStopOnFirstCatchEnabled();
    }

    private static String resolveDefaultQuantifier(TestScopedConfig classConfig, TestMethodConfig methodConfig) {
        if (methodConfig != null && methodConfig.settings != null
                && methodConfig.settings.default_quantifier != null) {
            return methodConfig.settings.default_quantifier;
        }
        if (classConfig != null && classConfig.settings != null
                && classConfig.settings.default_quantifier != null) {
            return classConfig.settings.default_quantifier;
        }
        return SimulatorConfig.getDefaultQuantifier();
    }

    // ── Fault resolution ──────────────────────────────────────────────────────

    private static List<FaultCollection> resolveEnabledFaults(
            TestScopedConfig classConfig, TestMethodConfig methodConfig) {

        List<FaultCollection> globalFaults = SimulatorConfig.getEnabledFaults();
        List<FaultCollection> result = new ArrayList<>();

        for (FaultCollection fault : FaultCollection.values()) {
            Boolean enabled = null;

            if (methodConfig != null && methodConfig.contract != null) {
                enabled = methodConfig.contract.getEnabled(fault);
            }
            if (enabled == null && classConfig != null && classConfig.contract != null) {
                enabled = classConfig.contract.getEnabled(fault);
            }
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
     * Merges invariants from all four levels (additive — all contribute):
     *   1. global config.yml
     *   2. features/*.yml  (each matching feature adds its invariants)
     *   3. class-level ClassName.metatest.yml
     *   4. method-level ClassName.metatest.yml
     */
    private static Map<String, List<InvariantConfig>> mergeInvariants(
            List<FeatureConfig> features,
            TestScopedConfig classConfig,
            TestMethodConfig methodConfig) {

        Map<String, List<InvariantConfig>> result = new HashMap<>();

        // 1. Global
        addEndpointInvariants(SimulatorConfig.getAllEndpointInvariants(), result);

        // 2. Features (each feature contributes independently)
        for (FeatureConfig feature : features) {
            if (feature.getInvariants() != null) {
                addEndpointInvariants(feature.getInvariants(), result);
            }
        }

        // 3. Class-level
        if (classConfig != null && classConfig.endpoints != null) {
            addEndpointInvariants(classConfig.endpoints, result);
        }

        // 4. Method-level
        if (methodConfig != null && methodConfig.endpoints != null) {
            addEndpointInvariants(methodConfig.endpoints, result);
        }

        return result;
    }

    static void addEndpointInvariants(
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

        if (classConfig != null && classConfig.exclusions != null
                && classConfig.exclusions.endpoints != null) {
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
}
