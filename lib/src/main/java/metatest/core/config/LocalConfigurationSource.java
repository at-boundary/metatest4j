package metatest.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


public class LocalConfigurationSource implements ConfigurationSource {

    private final SimulatorConfig config;
    private final List<Pattern> urlExcludePatterns;
    private final List<Pattern> endpointExcludePatterns;
    private final List<Pattern> testExcludePatterns;

    public LocalConfigurationSource() {
        this.config = loadConfig();
        // Support both new exclusions structure and legacy structure
        this.urlExcludePatterns = compilePatterns(getUrlExclusions());
        this.endpointExcludePatterns = compilePatterns(getEndpointExclusions());
        this.testExcludePatterns = compilePatterns(getTestExclusions());
    }

    private List<String> getUrlExclusions() {
        // New structure first
        if (config.exclusions != null && config.exclusions.urls != null) {
            return config.exclusions.urls;
        }
        // Legacy fallback
        if (config.url != null && config.url.exclude != null) {
            return config.url.exclude;
        }
        return null;
    }

    private List<String> getEndpointExclusions() {
        // New structure first
        if (config.exclusions != null && config.exclusions.endpoints != null) {
            return config.exclusions.endpoints;
        }
        // Note: config.endpoints is now a Map for invariants, not the legacy exclusion list
        return null;
    }

    private List<String> getTestExclusions() {
        // New structure first
        if (config.exclusions != null && config.exclusions.tests != null) {
            return config.exclusions.tests;
        }
        // Legacy fallback
        if (config.tests != null && config.tests.exclude != null) {
            return config.tests.exclude;
        }
        return null;
    }

    private SimulatorConfig loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        // Preferred location: metatest/contract.yml. Fall back to root-level for backward compat.
        String[] candidates = {"metatest/contract.yml", "contract.yml", "config.yml"};
        for (String filename : candidates) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
                if (is == null) continue;
                SimulatorConfig config = mapper.readValue(is, SimulatorConfig.class);
                System.out.println("[Metatest] Loaded configuration from " + filename);
                return config;
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse " + filename, e);
            }
        }
        throw new RuntimeException("No contract.yml found in classpath. " +
                "Create src/test/resources/metatest/contract.yml to configure fault injection.");
    }

    private List<Pattern> compilePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return new ArrayList<>();
        }

        List<Pattern> compiledPatterns = new ArrayList<>();
        for (String pattern : patterns) {
            // Convert glob-like patterns to regex
            // Example: '*/login*' becomes '.*\/login.*'
            String regex = pattern
                    .replace("*", ".*")
                    .replace("?", ".");
            compiledPatterns.add(Pattern.compile(regex));
        }
        return compiledPatterns;
    }

    @Override
    public List<FaultCollection> getEnabledFaults() {
        List<FaultCollection> enabledFaults = new ArrayList<>();

        if (config.contract == null) {
            System.out.println("[Metatest] No contract section found in contract.yml");
            return enabledFaults;
        }

        if (config.contract.null_field != null && config.contract.null_field.enabled) {
            enabledFaults.add(FaultCollection.null_field);
        }
        if (config.contract.missing_field != null && config.contract.missing_field.enabled) {
            enabledFaults.add(FaultCollection.missing_field);
        }
        if (config.contract.empty_list != null && config.contract.empty_list.enabled) {
            enabledFaults.add(FaultCollection.empty_list);
        }
        if (config.contract.empty_string != null && config.contract.empty_string.enabled) {
            enabledFaults.add(FaultCollection.empty_string);
        }
        if (config.contract.invalid_value != null && config.contract.invalid_value.enabled) {
            enabledFaults.add(FaultCollection.invalid_value);
        }
        if (config.contract.http_method_change != null && config.contract.http_method_change.enabled) {
            enabledFaults.add(FaultCollection.http_method_change);
        }

        System.out.println("[Metatest] Enabled faults from contract.yml: " + enabledFaults);
        return enabledFaults;
    }

    @Override
    public boolean isEndpointExcluded(String endpoint) {
        if (endpoint == null) {
            return false;
        }

        for (Pattern pattern : urlExcludePatterns) {
            if (pattern.matcher(endpoint).matches()) {
                return true;
            }
        }

        for (Pattern pattern : endpointExcludePatterns) {
            if (pattern.matcher(endpoint).matches()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isTestExcluded(String testName) {
        if (testName == null) {
            return false;
        }

        for (Pattern pattern : testExcludePatterns) {
            if (pattern.matcher(testName).matches()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String getSourceName() {
        return "Local YAML";
    }

    @Override
    public SimulatorConfig getConfig() {
        return config;
    }
}
