package metatest.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton cache for feature configurations.
 * Scans resources/metatest/features/ once on first access, then serves lookups
 * from an in-memory cache keyed by "className#methodName".
 *
 * Lookup logic for a given (testClass, methodName):
 *   1. Features that map this class with no methods specified (applies to all methods)
 *   2. Features that map this class with an exact method name match
 *   3. Features that map this class with a glob wildcard pattern match
 *
 * A test can match multiple features — all matching features contribute invariants.
 */
public class FeatureConfigCache {

    private static final FeatureConfigCache INSTANCE = new FeatureConfigCache();

    /** All loaded feature configs — small list, iterated per lookup */
    private volatile List<FeatureConfig> allFeatures = null;

    /** Resolved lookup cache keyed by "fqClassName#methodName" */
    private final ConcurrentHashMap<String, List<FeatureConfig>> lookupCache = new ConcurrentHashMap<>();

    private final Object initLock = new Object();

    private FeatureConfigCache() {}

    public static FeatureConfigCache getInstance() {
        return INSTANCE;
    }

    /**
     * Returns all features that apply to the given test class and method.
     * Results are cached after first resolution per class+method pair.
     */
    public List<FeatureConfig> getFeaturesFor(Class<?> testClass, String methodName) {
        ensureLoaded();
        if (allFeatures.isEmpty()) return List.of();

        String cacheKey = testClass.getName() + "#" + methodName;
        return lookupCache.computeIfAbsent(cacheKey, k ->
                resolveFeaturesFor(testClass.getName(), methodName));
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void ensureLoaded() {
        if (allFeatures != null) return;
        synchronized (initLock) {
            if (allFeatures != null) return;
            allFeatures = new FeatureConfigScanner().scanAll();
        }
    }

    private List<FeatureConfig> resolveFeaturesFor(String className, String methodName) {
        List<FeatureConfig> result = new ArrayList<>();

        for (FeatureConfig feature : allFeatures) {
            if (!feature.hasTests()) continue;

            for (FeatureTestMapping mapping : feature.getTests()) {
                if (!className.equals(mapping.getClassName())) continue;

                if (mapping.appliesToAllMethods()) {
                    // No methods specified — applies to every method in class
                    result.add(feature);
                    break;
                }

                // Check each method pattern
                boolean matched = false;
                for (String pattern : mapping.getMethods()) {
                    if (methodMatches(pattern, methodName)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) {
                    result.add(feature);
                    break;
                }
            }
        }

        return result;
    }

    private static boolean methodMatches(String pattern, String methodName) {
        if (!pattern.contains("*") && !pattern.contains("?")) {
            return pattern.equals(methodName);
        }
        // Glob → regex: escape dots, * → .*, ? → .
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return methodName.matches(regex);
    }
}
