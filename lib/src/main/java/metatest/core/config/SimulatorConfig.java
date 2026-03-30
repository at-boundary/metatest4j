package metatest.core.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class SimulatorConfig {
    /**
     * Config version
     */
    public String version;

    /**
     * Global settings (default quantifier, etc.)
     */
    public Settings settings;

    /**
     * Endpoint-specific invariant rules.
     * Structure: Map<endpoint_path, Map<http_method, MethodInvariantsConfig>>
     * Example: endpoints."/api/v1/orders/{id}".GET.invariants
     */
    public Map<String, Map<String, MethodInvariantsConfig>> endpoints = new HashMap<>();

    /**
     * Global contract-level fault injection settings.
     * Defined in contract.yml under the `contract:` key.
     */
    public Contract contract;

    /**
     * Simulation behavior settings
     */
    public Simulation simulation;

    /**
     * Exclusion patterns for endpoints, tests, and URLs
     */
    public Exclusions exclusions;

    /**
     * Report output configuration
     */
    public Report report;

    // Legacy fields (kept for backward compatibility during transition)
    public Semantics semantics;
    public SemanticFaults semanticFaults;
    public QualityAnalysis qualityAnalysis;
    public Url url;
    public Tests tests;

    @Data
    public static class Contract {
        static class Fault {
            public boolean enabled;
        }

        static class DelayInjection extends Fault {
            public int delay_ms;
        }

        public Fault null_field;
        public Fault missing_field;
        public Fault empty_list;
        public Fault empty_string;
        public Fault invalid_data_type;
        public Fault invalid_value;
        public Fault http_method_change;
        public Fault status_code_change;
        public DelayInjection delay_injection;
    }

    @Data
    public static class Semantics {
        public boolean enabled;
        public IntentAnalysis intent_analysis;
        public BusinessLogic business_logic;
        public Completeness completeness;
        public EdgeCases edge_cases;
        public TestLogic test_logic;
        public Mutations mutations;
        public QualityScoring quality_scoring;
    }

    @Data
    public static class IntentAnalysis {
        public boolean enabled;
        public boolean analyze_assertions;
        public boolean analyze_test_description;
        public boolean extract_business_rules;
    }

    @Data
    public static class BusinessLogic {
        public boolean enabled;
        public boolean validate_data_consistency;
        public boolean validate_state_transitions;
        public boolean validate_authorization;
        public boolean validate_data_integrity;
    }

    @Data
    public static class Completeness {
        public boolean enabled;
        public boolean detect_missing_scenarios;
        public boolean calculate_coverage_percentage;
        public boolean suggest_additional_tests;
    }

    @Data
    public static class EdgeCases {
        public boolean enabled;
        public boolean boundary_conditions;
        public boolean special_characters;
        public boolean null_handling;
        public boolean empty_handling;
        public boolean large_data_handling;
    }

    @Data
    public static class TestLogic {
        public boolean enabled;
        public boolean detect_hardcoded_values;
        public boolean check_incomplete_assertions;
        public boolean validate_test_isolation;
        public boolean detect_race_conditions;
    }

    @Data
    public static class Mutations {
        public boolean enabled;
        public boolean business_logic_mutations;
        public boolean data_invariantship_mutations;
        public boolean authorization_mutations;
    }

    @Data
    public static class QualityScoring {
        public boolean enabled;
        public double semantic_coverage_threshold;
        public double edge_case_coverage_threshold;
        public double business_logic_coverage_threshold;
        public double maintainability_threshold;
        public double reliability_threshold;
    }

    @Data
    public static class SemanticFaults {
        public boolean enabled;
        public StatusCodes status_codes;
        public FieldValues field_values;
        public Performance performance;
        public BusinessLogicFaults business_logic;
        public DataRelationships data_invariantships;
    }

    @Data
    public static class StatusCodes {
        public boolean enabled;
        public List<Integer> codes;
    }
    @Data
    public static class FieldValues {
        public boolean enabled;
        public List<String> invalid_values;
    }

    @Data
    public static class Performance {
        public boolean enabled;
        public List<Integer> delays_ms;
    }

    @Data
    public static class BusinessLogicFaults {
        public boolean enabled;
        public boolean reverse_order_status;
        public boolean swap_user_roles;
        public boolean invert_payment_amount;
    }

    @Data
    public static class DataRelationships {
        public boolean enabled;
        public boolean swap_parent_child;
        public boolean break_foreign_key;
    }

    @Data
    public static class QualityAnalysis {
        public boolean enabled;
        public AntiPatterns anti_patterns;
        public Recommendations recommendations;
    }

    @Data
    public static class AntiPatterns {
        public boolean enabled;
        public boolean hardcoded_values;
        public boolean incomplete_assertions;
        public boolean missing_cleanup;
        public boolean race_conditions;
    }

    @Data
    public static class Recommendations {
        public boolean enabled;
        public boolean suggest_semantic_assertions;
        public boolean suggest_edge_case_tests;
        public boolean suggest_missing_scenarios;
        public boolean suggest_improvements;
    }

    /**
     * Global settings for invariant evaluation
     */
    @Data
    public static class Settings {
        /**
         * Default quantifier for array fields: "all" (default), "any", "none"
         */
        public String default_quantifier = "all";

        /**
         * If true, skip simulation for a fault once any test has caught it.
         * Useful for quick/smoke runs where you just want to know if at least one test catches each fault.
         * Default: false (run all simulations for full coverage information)
         */
        public boolean stop_on_first_catch = false;
    }

    /**
     * Exclusion patterns for endpoints, URLs, and tests
     */
    @Data
    public static class Exclusions {
        /**
         * URL patterns to exclude from simulation (glob patterns)
         */
        public List<String> urls;

        /**
         * Endpoint patterns to exclude from simulation (glob patterns)
         */
        public List<String> endpoints;

        /**
         * Test name patterns to exclude from simulation (glob patterns)
         */
        public List<String> tests;
    }

    // Legacy exclusion classes (kept for backward compatibility)
    @Data
    public static class Url {
        public List<String> exclude;
    }

    @Data
    public static class Endpoints {
        public List<String> exclude;
    }

    @Data
    public static class Tests {
        public List<String> exclude;
    }

    @Data
    public static class Simulation {
        public List<Integer> allowed_status_codes;
        public boolean only_success_responses;
        public boolean skip_collections_response;
        public int min_response_fields;
        public List<String> skip_if_contains_fields;
        public MultipleEndpointsStrategy multiple_endpoints_strategy;
    }

    @Data
    public static class MultipleEndpointsStrategy {
        public boolean test_only_last_endpoint = true; // default: true
        public List<String> exclude_endpoints; // regex patterns
    }

    @Data
    public static class Report {
        public String format;
        public String output_path;
        public SemanticReport semantic_report;
    }

    @Data
    public static class SemanticReport {
        public boolean enabled;
        public boolean include_quality_score;
        public boolean include_recommendations;
        public boolean include_business_logic_report;
        public boolean include_completeness_report;
        public boolean include_edge_case_report;
    }

    private static final ConfigurationSource configSource = initializeConfigurationSource();

    private static ConfigurationSource initializeConfigurationSource() {
        String configSourceType = System.getProperty("metatest.config.source");

        if (configSourceType == null) {
            configSourceType = System.getenv("METATEST_CONFIG_SOURCE");
        }

        if (configSourceType == null) {
            configSourceType = readConfigSourceFromPropertiesFile();
        }

        ConfigurationSource source;

        if ("local".equalsIgnoreCase(configSourceType)) {
            System.out.println("Using LOCAL configuration source (explicitly set via metatest.config.source)");
            source = new LocalConfigurationSource();
        } else if ("api".equalsIgnoreCase(configSourceType)) {
            System.out.println("Using API configuration source (explicitly set via metatest.config.source)");
            source = new ApiConfigurationSource();
        } else {
            // Auto-detect based on API configuration
            MetaTestConfig metaTestConfig = MetaTestConfig.getInstance();
            if (metaTestConfig.isApiConfigured()) {
                System.out.println("Using API configuration source (auto-detected - API key present)");
                source = new ApiConfigurationSource();
            } else {
                System.out.println("Using LOCAL configuration source (auto-detected - no API key)");
                source = new LocalConfigurationSource();
            }
        }

        System.out.println("Configuration source initialized: " + source.getSourceName());
        return source;
    }

    private static String readConfigSourceFromPropertiesFile() {
        String[] paths = {"metatest/metatest.properties", "metatest.properties"};
        for (String path : paths) {
            try (java.io.InputStream is = SimulatorConfig.class.getClassLoader().getResourceAsStream(path)) {
                if (is == null) continue;
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String value = props.getProperty("metatest.config.source");
                if (value != null && !value.trim().isEmpty()) return value.trim();
            } catch (java.io.IOException ignored) {}
        }
        return null;
    }

    public static List<FaultCollection> getEnabledFaults(){
        return configSource.getEnabledFaults();
    }

    // TODO
    public static boolean isSemanticsEnabled() {
        return false; // Disabled for now, will be implemented with business-rules and semantic endpoints
    }

    public static boolean isIntentAnalysisEnabled() {
        return false;
    }

    public static boolean isBusinessLogicValidationEnabled() {
        return false;
    }

    public static boolean isCompletenessAnalysisEnabled() {
        return false;
    }

    public static boolean isEdgeCaseDetectionEnabled() {
        return false;
    }

    public static boolean isTestLogicAnalysisEnabled() {
        return false;
    }

    public static boolean isSemanticMutationsEnabled() {
        return false;
    }

    public static boolean isQualityScoringEnabled() {
        return false;
    }

    public static boolean isSemanticFaultsEnabled() {
        return false;
    }

    public static boolean isQualityAnalysisEnabled() {
        return false;
    }

    public static List<Integer> getSemanticStatusCodes() {
        return new ArrayList<>();
    }

    public static List<String> getSemanticInvalidValues() {
        return new ArrayList<>();
    }

    public static List<Integer> getSemanticDelays() {
        return new ArrayList<>();
    }

    public static double getSemanticCoverageThreshold() {
        return 0.7; // default
    }

    public static double getEdgeCaseCoverageThreshold() {
        return 0.5; // default
    }

    public static boolean isEndpointExcluded(String endpoint){
        return configSource.isEndpointExcluded(endpoint);
    }

    public static boolean isTestExcluded(String testName){
        return configSource.isTestExcluded(testName);
    }

    /**
     * Checks if a response should have fault simulation based on its status code and content.
     *
     * @param statusCode The HTTP status code of the response
     * @param responseMap The parsed response body as a map
     * @param responseBody The raw response body (to check if it's a collection)
     * @return true if simulation should proceed, false if it should be skipped
     */
    public static boolean shouldSimulateResponse(int statusCode, Map<String, Object> responseMap, String responseBody) {
        SimulatorConfig config = configSource.getConfig();

        // If no simulation config, default to only 2xx responses and skip collections
        if (config == null || config.simulation == null) {
            boolean is2xx = statusCode >= 200 && statusCode < 300;
            boolean isCollection = isCollectionResponse(responseBody);
            if (isCollection) {
                System.out.println("[Metatest-Sim] Skipping simulation - response is a collection (array)");
                return false;
            }
            return is2xx;
        }

        Simulation simConfig = config.simulation;

        if (simConfig.only_success_responses) {
            if (statusCode < 200 || statusCode >= 300) {
                System.out.println("[Metatest-Sim] Skipping simulation - non-success status code: " + statusCode);
                return false;
            }
        } else if (simConfig.allowed_status_codes != null && !simConfig.allowed_status_codes.isEmpty()) {
            if (!simConfig.allowed_status_codes.contains(statusCode)) {
                System.out.println("[Metatest-Sim] Skipping simulation - status code not in allowed list: " + statusCode);
                return false;
            }
        }

        if (simConfig.skip_collections_response && isCollectionResponse(responseBody)) {
            System.out.println("[Metatest-Sim] Skipping simulation - response is a collection (array)");
            return false;
        }

        if (responseMap == null || responseMap.isEmpty()) {
            if (simConfig.min_response_fields > 0) {
                System.out.println("[Metatest-Sim] Skipping simulation - empty response body");
                return false;
            }
        } else if (responseMap.size() < simConfig.min_response_fields) {
            System.out.println("[Metatest-Sim] Skipping simulation - response has fewer than " +
                simConfig.min_response_fields + " fields (" + responseMap.size() + " found)");
            return false;
        }

        if (simConfig.skip_if_contains_fields != null && responseMap != null) {
            for (String errorField : simConfig.skip_if_contains_fields) {
                if (responseMap.containsKey(errorField)) {
                    System.out.println("[Metatest-Sim] Skipping simulation - response contains error field: " + errorField);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if a response body represents a collection (array) rather than a single object.
     *
     * @param responseBody The raw response body string
     * @return true if the response is a JSON array
     */
    private static boolean isCollectionResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return false;
        }

        // Trim whitespace and check if response starts with '[' (JSON array)
        String trimmed = responseBody.trim();
        return trimmed.startsWith("[");
    }

    /**
     * Gets the multiple endpoints strategy configuration.
     * If not configured, returns default (test_only_last_endpoint = true).
     *
     * @return The multiple endpoints strategy configuration
     */
    public static MultipleEndpointsStrategy getMultipleEndpointsStrategy() {
        SimulatorConfig config = configSource.getConfig();
        if (config != null && config.simulation != null && config.simulation.multiple_endpoints_strategy != null) {
            return config.simulation.multiple_endpoints_strategy;
        }

        // Return default configuration
        MultipleEndpointsStrategy defaultStrategy = new MultipleEndpointsStrategy();
        defaultStrategy.test_only_last_endpoint = true;
        defaultStrategy.exclude_endpoints = new ArrayList<>();
        return defaultStrategy;
    }

    /**
     * Gets invariant rules for a specific endpoint and HTTP method.
     *
     * @param endpointPath The endpoint path (e.g., "/api/v1/orders/{order_id}")
     * @param httpMethod The HTTP method (e.g., "GET", "POST")
     * @return List of invariant configs, empty list if none configured
     */
    public static List<InvariantConfig> getInvariantsForEndpoint(String endpointPath, String httpMethod) {
        SimulatorConfig config = configSource.getConfig();
        if (config == null || config.endpoints == null) {
            return new ArrayList<>();
        }

        Map<String, MethodInvariantsConfig> methodConfigs = config.endpoints.get(endpointPath);
        if (methodConfigs == null) {
            return new ArrayList<>();
        }

        MethodInvariantsConfig methodConfig = methodConfigs.get(httpMethod.toUpperCase());
        if (methodConfig == null || methodConfig.getInvariants() == null) {
            return new ArrayList<>();
        }

        return methodConfig.getInvariants();
    }

    /**
     * Gets the default quantifier for array field evaluations.
     *
     * @return The default quantifier ("all", "any", or "none"), defaults to "all"
     */
    public static String getDefaultQuantifier() {
        SimulatorConfig config = configSource.getConfig();
        if (config != null && config.settings != null && config.settings.default_quantifier != null) {
            return config.settings.default_quantifier;
        }
        return "all";
    }

    /**
     * Checks if stop_on_first_catch optimization is enabled.
     * When enabled, once a fault is caught by any test, it will be skipped for remaining tests.
     *
     * @return true if stop_on_first_catch is enabled, false otherwise
     */
    public static boolean isStopOnFirstCatchEnabled() {
        SimulatorConfig config = configSource.getConfig();
        if (config != null && config.settings != null) {
            return config.settings.stop_on_first_catch;
        }
        return false;
    }

    /**
     * Checks if there are any invariant rules configured for the given endpoint and method.
     *
     * @param endpointPath The endpoint path
     * @param httpMethod The HTTP method
     * @return true if invariants are configured
     */
    public static boolean hasInvariants(String endpointPath, String httpMethod) {
        return !getInvariantsForEndpoint(endpointPath, httpMethod).isEmpty();
    }

    /**
     * Gets all configured endpoints with invariants.
     *
     * @return Map of endpoint paths to their method configurations
     */
    public static Map<String, Map<String, MethodInvariantsConfig>> getAllEndpointInvariants() {
        SimulatorConfig config = configSource.getConfig();
        if (config == null || config.endpoints == null) {
            return new HashMap<>();
        }
        return config.endpoints;
    }
}
