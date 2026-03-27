package metatest.core.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-test-method overrides within a .metatest.yml file.
 * Keys in the parent `tests:` map can be exact method names or glob patterns (e.g. "testGet*").
 *
 * Example:
 * <pre>
 * tests:
 *   testGetFilledOrder:
 *     endpoints:
 *       /api/orders/{id}:
 *         GET:
 *           invariants:
 *             - name: filled_at_set
 *               field: filled_at
 *               is_not_null: true
 *
 *   "testGet*":
 *     faults:
 *       empty_list: { enabled: true }
 *
 *   testHealthCheck:
 *     exclude: true
 * </pre>
 */
@Data
public class TestMethodConfig {

    /**
     * If true, skip fault simulation entirely for this test method.
     * The test still runs normally; no mutations are applied.
     */
    public boolean exclude = false;

    /** Overrides settings for this specific method */
    public SimulatorConfig.Settings settings;

    /**
     * Method-level fault overrides.
     * Merged over class-level and global faults (most specific wins per fault type).
     */
    public TestScopedConfig.FaultsOverride faults;

    /**
     * Method-level endpoint invariants.
     * Merged additively on top of class-level and global invariants.
     */
    public Map<String, Map<String, MethodInvariantsConfig>> endpoints = new HashMap<>();

    /** Additional exclusions for this method */
    public TestScopedConfig.ExclusionsOverride exclusions;
}
