package metatest.core.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root model for feature-level configuration files.
 * Placed in: src/test/resources/metatest/features/<feature-name>.yml
 *
 * A feature groups related business invariants with the tests that exercise them.
 * Multiple test classes can be mapped to the same feature, and a test class can
 * belong to multiple features.
 *
 * Invariants defined here are merged additively with:
 *   - global config.yml invariants
 *   - class-level .metatest.yml invariants
 *   - method-level .metatest.yml invariants
 *
 * Example:
 * <pre>
 * feature: Order Lifecycle
 * description: Rules governing order state transitions and data consistency
 *
 * invariants:
 *   /api/v1/orders/{id}:
 *     GET:
 *       - name: filled_order_has_filled_at
 *         if:
 *           field: status
 *           equals: FILLED
 *         then:
 *           field: filled_at
 *           is_not_null: true
 *
 * tests:
 *   - class: com.example.orders.OrderApiTest
 *     methods:
 *       - testGetFilledOrder
 *       - "testCreate*"
 *   - class: com.example.orders.OrderAdminTest
 * </pre>
 */
@Data
public class FeatureConfig {

    /** Display name of the feature (used in reports) */
    private String feature;

    /** Optional human-readable description of what this feature covers */
    private String description;

    /**
     * Invariant rules grouped by endpoint path and HTTP method.
     * Structure: Map<endpoint_path, Map<http_method, MethodInvariantsConfig>>
     * Same DSL as config.yml endpoints section.
     */
    private Map<String, Map<String, MethodInvariantsConfig>> invariants = new HashMap<>();

    /**
     * Test classes and methods that exercise this feature.
     * These drive which simulations receive this feature's invariants.
     */
    private List<FeatureTestMapping> tests = new ArrayList<>();

    public boolean hasInvariants() {
        return invariants != null && !invariants.isEmpty();
    }

    public boolean hasTests() {
        return tests != null && !tests.isEmpty();
    }
}
