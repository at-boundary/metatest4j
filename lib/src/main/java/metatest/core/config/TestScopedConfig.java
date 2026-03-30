package metatest.core.config;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root model for per-test-class .metatest.yml configuration files.
 * Placed in: src/test/resources/metatest/<fully.qualified.ClassName>.metatest.yml
 *
 * Class-level settings apply to ALL tests in the class.
 * Method-level overrides live under the `tests:` key.
 */
@Data
public class TestScopedConfig {

    public String version;

    /** Overrides global settings for this test class */
    public SimulatorConfig.Settings settings;

    /**
     * Class-level contract (fault injection) overrides.
     * Unspecified faults fall back to global contract.yml values.
     */
    public ContractOverride contract;

    /**
     * Class-level endpoint invariants.
     * Merged additively with global config.yml invariants.
     * Structure: Map<endpoint_path, Map<http_method, MethodInvariantsConfig>>
     */
    public Map<String, Map<String, MethodInvariantsConfig>> endpoints = new HashMap<>();

    /** Class-level exclusions merged (union) with global exclusions */
    public ExclusionsOverride exclusions;

    /**
     * Per-method overrides.
     * Keys can be exact method names or glob patterns (e.g. "testGet*").
     * Exact matches take precedence over wildcards.
     */
    public Map<String, TestMethodConfig> tests = new HashMap<>();

    /**
     * Contract (fault injection) enable/disable overrides.
     * Each fault type is optional — if absent, falls back to the parent level.
     */
    @Data
    public static class ContractOverride {
        public FaultSwitch null_field;
        public FaultSwitch missing_field;
        public FaultSwitch empty_list;
        public FaultSwitch empty_string;
        public FaultSwitch invalid_value;
        public FaultSwitch http_method_change;

        @Data
        public static class FaultSwitch {
            public Boolean enabled;
        }

        /** Returns the enabled value for a given FaultCollection, or null if not configured here */
        public Boolean getEnabled(FaultCollection fault) {
            FaultSwitch sw = switch (fault) {
                case null_field -> null_field;
                case missing_field -> missing_field;
                case empty_list -> empty_list;
                case empty_string -> empty_string;
                case invalid_value -> invalid_value;
                case http_method_change -> http_method_change;
            };
            return sw != null ? sw.enabled : null;
        }
    }

    /** Additive exclusions on top of the global config */
    @Data
    public static class ExclusionsOverride {
        /** Endpoint patterns to additionally exclude (glob) */
        public List<String> endpoints;
    }
}
