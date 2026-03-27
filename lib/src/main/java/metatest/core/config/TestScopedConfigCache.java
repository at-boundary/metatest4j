package metatest.core.config;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton cache for per-test-class .metatest.yml configs.
 * Loads lazily on first interception of each test class and caches for the JVM lifetime.
 *
 * Optional.empty() is also cached to avoid repeated classloader lookups for classes
 * that have no .metatest.yml file.
 */
public class TestScopedConfigCache {

    private static final TestScopedConfigCache INSTANCE = new TestScopedConfigCache();

    private final TestScopedConfigLoader loader = new TestScopedConfigLoader();
    private final ConcurrentHashMap<String, Optional<TestScopedConfig>> cache = new ConcurrentHashMap<>();

    private TestScopedConfigCache() {}

    public static TestScopedConfigCache getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the .metatest.yml config for the given test class, loading it on first call.
     *
     * @param testClass The test class being intercepted
     * @return Optional containing the config, or empty if no .metatest.yml exists
     */
    public Optional<TestScopedConfig> get(Class<?> testClass) {
        return cache.computeIfAbsent(testClass.getName(), k -> loader.load(testClass));
    }
}
