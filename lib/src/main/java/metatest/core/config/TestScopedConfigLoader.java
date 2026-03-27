package metatest.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Loads per-test-class .metatest.yml files from the classpath.
 *
 * Convention:
 *   File: src/test/resources/metatest/<fully.qualified.ClassName>.metatest.yml
 *   Example: src/test/resources/metatest/com.example.orders.OrderApiTest.metatest.yml
 *
 * Files are discovered lazily when the test class is first intercepted.
 * Results (including "not found") are cached by TestScopedConfigCache.
 */
public class TestScopedConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * Attempts to load a .metatest.yml for the given test class.
     *
     * @param testClass The test class being intercepted
     * @return Optional containing the parsed config, or empty if no file found
     */
    public Optional<TestScopedConfig> load(Class<?> testClass) {
        String resourcePath = "metatest/" + testClass.getName() + ".metatest.yml";

        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            // Also try the class's own classloader as fallback
            is = testClass.getClassLoader().getResourceAsStream(resourcePath);
        }

        if (is == null) {
            return Optional.empty();
        }

        try (InputStream stream = is) {
            TestScopedConfig config = YAML_MAPPER.readValue(stream, TestScopedConfig.class);
            System.out.println("[Metatest] Loaded test-scoped config: " + resourcePath);
            return Optional.of(config);
        } catch (IOException e) {
            System.err.println("[Metatest] Failed to parse " + resourcePath + ": " + e.getMessage());
            return Optional.empty();
        }
    }
}
