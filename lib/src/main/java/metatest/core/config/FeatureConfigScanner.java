package metatest.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans src/test/resources/metatest/features/ for all *.yml feature files.
 *
 * Uses a multi-strategy approach to locate the directory because ClassLoader
 * directory resolution is inconsistent across JVM versions and build tools:
 *
 *   1. ClassLoader.getResource("metatest/features")      — standard, works on most JVMs
 *   2. ClassLoader.getResources("metatest/features")     — multi-classloader variant
 *   3. java.class.path system property scanning          — reliable fallback for Gradle workers
 */
public class FeatureConfigScanner {

    private static final String FEATURES_RELATIVE = "metatest/features";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public List<FeatureConfig> scanAll() {
        List<FeatureConfig> features = new ArrayList<>();

        for (File dir : resolveDirectories()) {
            scanDirectory(dir, features);
        }

        if (!features.isEmpty()) {
            System.out.println("[Metatest] Loaded " + features.size()
                    + " feature file(s) from metatest/features/");
        }

        return features;
    }

    // ── Directory resolution ──────────────────────────────────────────────────

    private Set<File> resolveDirectories() {
        Set<File> dirs = new LinkedHashSet<>();

        // Strategy 1: ClassLoader.getResource (singular)
        resolveViaGetResource(Thread.currentThread().getContextClassLoader(), dirs);
        resolveViaGetResource(FeatureConfigScanner.class.getClassLoader(), dirs);

        // Strategy 2: ClassLoader.getResources (plural — multiple classpath entries)
        resolveViaGetResources(Thread.currentThread().getContextClassLoader(), dirs);

        // Strategy 3: java.class.path system property (reliable for Gradle workers)
        resolveViaClasspath(dirs);

        return dirs;
    }

    private void resolveViaGetResource(ClassLoader cl, Set<File> dirs) {
        if (cl == null) return;
        for (String candidate : new String[]{FEATURES_RELATIVE, FEATURES_RELATIVE + "/"}) {
            URL url = cl.getResource(candidate);
            toFile(url).ifPresent(dirs::add);
        }
    }

    private void resolveViaGetResources(ClassLoader cl, Set<File> dirs) {
        if (cl == null) return;
        try {
            Enumeration<URL> urls = cl.getResources(FEATURES_RELATIVE);
            while (urls.hasMoreElements()) {
                toFile(urls.nextElement()).ifPresent(dirs::add);
            }
        } catch (IOException ignored) {}
    }

    private void resolveViaClasspath(Set<File> dirs) {
        String classpath = System.getProperty("java.class.path");
        if (classpath == null || classpath.isBlank()) return;

        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            File root = new File(entry.trim());
            if (!root.isDirectory()) continue;
            File candidate = new File(root, FEATURES_RELATIVE.replace("/", File.separator));
            if (candidate.isDirectory()) {
                dirs.add(candidate);
            }
        }
    }

    private java.util.Optional<File> toFile(URL url) {
        if (url == null || !"file".equals(url.getProtocol())) return java.util.Optional.empty();
        try {
            File f = new File(url.toURI());
            return f.isDirectory() ? java.util.Optional.of(f) : java.util.Optional.empty();
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    // ── File parsing ──────────────────────────────────────────────────────────

    private void scanDirectory(File dir, List<FeatureConfig> features) {
        File[] files = dir.listFiles(f ->
                f.isFile() && (f.getName().endsWith(".yml") || f.getName().endsWith(".yaml")));

        if (files == null) return;

        for (File file : files) {
            try (InputStream is = new FileInputStream(file)) {
                FeatureConfig config = YAML_MAPPER.readValue(is, FeatureConfig.class);
                if (config.getFeature() == null || config.getFeature().isBlank()) {
                    System.err.println("[Metatest] Skipping feature file (missing 'feature:' field): "
                            + file.getName());
                    continue;
                }
                features.add(config);
                System.out.println("[Metatest] Loaded feature: '" + config.getFeature()
                        + "' (" + file.getName() + ")");
            } catch (IOException e) {
                System.err.println("[Metatest] Failed to parse feature file '"
                        + file.getName() + "': " + e.getMessage());
            }
        }
    }
}
