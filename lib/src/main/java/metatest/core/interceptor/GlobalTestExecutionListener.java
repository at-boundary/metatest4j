package metatest.core.interceptor;

import metatest.simulation.FaultSimulationReport;
import metatest.coverage.Collector;
import metatest.analytics.GapAnalyzer;
import metatest.report.HtmlReportGenerator;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

public class GlobalTestExecutionListener implements TestExecutionListener {

    private static boolean executed = false;
    private final boolean runWithMetatest = Boolean.parseBoolean(System.getProperty("runWithMetatest"));

    public GlobalTestExecutionListener() {
        System.out.println("[MetaTest] GlobalTestExecutionListener initialized. runWithMetatest=" + runWithMetatest);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        System.out.println("[MetaTest] testPlanExecutionFinished called. executed=" + executed + ", runWithMetatest=" + runWithMetatest);
        if (!executed && runWithMetatest) {
            executed = true;
            System.out.println("[MetaTest] All tests completed - Generating reports...");
//            FaultSimulationReport.getInstance().sendResultsToAPI();

            // Print console summary before writing file reports
            FaultSimulationReport.getInstance().printConsoleSummary();

            // Generate JSON reports first
            FaultSimulationReport.getInstance().createJSONReport();
            Collector.saveCoverageReport();
            GapAnalyzer.generateGapReport();

            // Generate HTML report after all JSON reports are created
            try {
                System.out.println("[MetaTest] Generating HTML report...");
                HtmlReportGenerator.generateReport("metatest_report.html");
                System.out.println("[MetaTest] HTML report generated successfully: metatest_report.html");
            } catch (Exception e) {
                System.err.println("[MetaTest] Failed to generate HTML report: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("[MetaTest] All reports generated successfully!");
        } else {
            System.out.println("[MetaTest] Skipping report generation (executed=" + executed + ", runWithMetatest=" + runWithMetatest + ")");
        }
    }
}