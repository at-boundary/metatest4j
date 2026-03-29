package metatest.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Maps a test class (and optionally specific methods) to a feature.
 * Used inside the `tests:` block of a feature YAML file.
 *
 * Examples:
 * <pre>
 * tests:
 *   # All methods in class
 *   - class: com.example.orders.OrderApiTest
 *
 *   # Specific methods (exact names or glob patterns)
 *   - class: com.example.orders.OrderAdminTest
 *     methods:
 *       - testGetFilledOrder
 *       - "testCreate*"
 * </pre>
 */
@Data
public class FeatureTestMapping {

    /**
     * Fully-qualified class name of the test class.
     * Example: com.example.orders.OrderApiTest
     */
    @JsonProperty("class")
    private String className;

    /**
     * Optional list of method names to include.
     * Supports exact names and glob patterns (e.g. "testGet*").
     * If null or empty, the feature applies to ALL methods in the class.
     */
    private List<String> methods;

    public boolean appliesToAllMethods() {
        return methods == null || methods.isEmpty();
    }
}
