package metatest.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The fully-merged configuration for a single test method execution.
 * Produced by ConfigResolver by combining: global config.yml + class .metatest.yml + method override.
 *
 * Resolution chain (most specific wins or additive depending on aspect):
 *   method-level → class-level → global config.yml
 *
 * - invariants: additive (all levels contribute)
 * - faults:     override (most specific wins per fault type)
 * - settings:   override (most specific wins per field)
 * - exclusions: additive (union)
 */
public class ResolvedTestConfig {

    /** Sentinel: simulation should be skipped entirely for this test (exclude: true) */
    public static final ResolvedTestConfig SKIP = new ResolvedTestConfig(
            true, false, "all", List.of(), Map.of(), List.of()
    );

    private final boolean skip;
    private final boolean stopOnFirstCatch;
    private final String defaultQuantifier;
    private final List<FaultCollection> enabledFaults;

    /**
     * Merged invariants keyed by "endpointPattern::HTTP_METHOD".
     * Example key: "/api/v1/orders/{id}::GET"
     */
    private final Map<String, List<InvariantConfig>> invariants;

    /** Compiled patterns for additionally excluded endpoints */
    private final List<Pattern> excludedEndpointPatterns;

    public ResolvedTestConfig(
            boolean skip,
            boolean stopOnFirstCatch,
            String defaultQuantifier,
            List<FaultCollection> enabledFaults,
            Map<String, List<InvariantConfig>> invariants,
            List<Pattern> excludedEndpointPatterns) {
        this.skip = skip;
        this.stopOnFirstCatch = stopOnFirstCatch;
        this.defaultQuantifier = defaultQuantifier;
        this.enabledFaults = Collections.unmodifiableList(new ArrayList<>(enabledFaults));
        this.invariants = Collections.unmodifiableMap(invariants);
        this.excludedEndpointPatterns = Collections.unmodifiableList(new ArrayList<>(excludedEndpointPatterns));
    }

    public boolean isSkip() {
        return skip;
    }

    public boolean isStopOnFirstCatch() {
        return stopOnFirstCatch;
    }

    public String getDefaultQuantifier() {
        return defaultQuantifier;
    }

    public List<FaultCollection> getEnabledFaults() {
        return enabledFaults;
    }

    /**
     * Returns the merged (additive) invariants for a specific endpoint and HTTP method.
     *
     * @param endpointPattern Normalized endpoint pattern, e.g. "/api/v1/orders/{id}"
     * @param httpMethod      HTTP method, e.g. "GET"
     * @return Combined list of invariants from global + class + method configs
     */
    public List<InvariantConfig> getInvariantsFor(String endpointPattern, String httpMethod) {
        String key = endpointPattern + "::" + httpMethod.toUpperCase();
        return invariants.getOrDefault(key, List.of());
    }

    /**
     * Checks if an endpoint is additionally excluded by the test-scoped config.
     * This is checked on top of the global exclusion rules in SimulatorConfig.
     */
    public boolean isEndpointExcluded(String endpoint) {
        if (endpoint == null) return false;
        for (Pattern pattern : excludedEndpointPatterns) {
            if (pattern.matcher(endpoint).matches()) return true;
        }
        return false;
    }

    /**
     * Returns true if this resolved config has any invariants configured
     * (across all endpoints/methods), used for logging.
     */
    public boolean hasAnyInvariants() {
        return !invariants.isEmpty();
    }
}
