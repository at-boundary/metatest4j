package metatest.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Data
public class CoverageConfig {

    private static CoverageConfig INSTANCE;
    private static final String CONFIG_FILE = "metatest/coverage_config.yml";

    private Coverage coverage;

    @Data
    public static class Coverage {
        private boolean enabled = true;
        private String output_file = "schema_coverage.json";
        private List<String> urls = new ArrayList<>();
        private List<String> exclude_endpoints = new ArrayList<>();
        private boolean include_request_body = true;
        private boolean include_response_body = false;
        private boolean aggregate_by_pattern = true;
        private GapAnalysis gap_analysis;
    }

    @Data
    public static class GapAnalysis {
        private boolean enabled = false;
        private String openapi_spec_path;
        private String output_file = "gap_analysis.json";
    }

    private List<Pattern> excludePatterns;

    public static synchronized CoverageConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = loadConfig();
        }
        return INSTANCE;
    }

    private static CoverageConfig loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream is = CoverageConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                System.out.println("[Coverage] No coverage_config.yml found, using defaults");
                CoverageConfig config = new CoverageConfig();
                config.coverage = new Coverage();
                return config;
            }

            CoverageConfig config = mapper.readValue(is, CoverageConfig.class);
            config.compileExclusionPatterns();
            System.out.println("[Coverage] Loaded configuration from " + CONFIG_FILE);
            return config;

        } catch (IOException e) {
            System.err.println("[Coverage] Failed to load coverage_config.yml: " + e.getMessage());
            CoverageConfig config = new CoverageConfig();
            config.coverage = new Coverage();
            return config;
        }
    }

    private void compileExclusionPatterns() {
        excludePatterns = new ArrayList<>();
        if (coverage != null && coverage.exclude_endpoints != null) {
            for (String pattern : coverage.exclude_endpoints) {
                // Convert glob-like patterns to regex
                String regex = pattern
                        .replace("*", ".*")
                        .replace("?", ".");
                excludePatterns.add(Pattern.compile(regex));
            }
        }
    }

    public boolean isEnabled() {
        return coverage != null && coverage.enabled;
    }

    public boolean isEndpointExcluded(String endpoint) {
        if (endpoint == null || excludePatterns == null) {
            return false;
        }

        for (Pattern pattern : excludePatterns) {
            if (pattern.matcher(endpoint).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean isUrlTracked(String url) {
        if (coverage == null || coverage.urls == null || coverage.urls.isEmpty()) {
            return true; // Empty list = track all
        }

        if (url == null) {
            return false;
        }

        for (String trackedUrl : coverage.urls) {
            if (url.startsWith(trackedUrl)) {
                return true;
            }
        }
        return false;
    }

    public String getOutputFile() {
        return coverage != null ? coverage.output_file : "schema_coverage.json";
    }

    public boolean shouldIncludeRequestBody() {
        return coverage != null && coverage.include_request_body;
    }

    public boolean shouldIncludeResponseBody() {
        return coverage != null && coverage.include_response_body;
    }

    public boolean shouldAggregateByPattern() {
        return coverage != null && coverage.aggregate_by_pattern;
    }

    public boolean isGapAnalysisEnabled() {
        return coverage != null && coverage.gap_analysis != null && coverage.gap_analysis.enabled;
    }

    public String getGapAnalysisSpecPath() {
        return coverage != null && coverage.gap_analysis != null ? coverage.gap_analysis.openapi_spec_path : null;
    }

    public String getGapAnalysisOutputFile() {
        return coverage != null && coverage.gap_analysis != null ? coverage.gap_analysis.output_file : "gap_analysis.json";
    }
}
