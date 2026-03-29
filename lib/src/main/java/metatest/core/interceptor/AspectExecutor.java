package metatest.core.interceptor;

import metatest.core.config.ConfigResolver;
import metatest.core.config.ResolvedTestConfig;
import metatest.core.config.SimulatorConfig;
import metatest.core.config.TestScopedConfig;
import metatest.core.config.TestScopedConfigCache;
import metatest.http.HTTPFactory;
import metatest.http.Request;
import metatest.http.Response;
import metatest.simulation.Runner;
import metatest.core.interceptor.TestContext;
import metatest.core.interceptor.TestContextManager;
import metatest.coverage.Logger;

import java.util.Optional;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

@Aspect
public class AspectExecutor {

    @Around("execution(@org.junit.jupiter.api.Test * *(..))")
    public Object interceptTestMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Class<?> testClass = joinPoint.getSignature().getDeclaringType();

        // Resolve per-test config (lazy-loaded, cached per class)
        Optional<TestScopedConfig> classConfig = TestScopedConfigCache.getInstance().get(testClass);
        ResolvedTestConfig resolvedConfig = ConfigResolver.resolve(testClass, classConfig, methodName);

        // Short-circuit: test is excluded from simulation via .metatest.yml
        if (resolvedConfig.isSkip()) {
            System.out.println("[Metatest] Simulation excluded via .metatest.yml for: " + methodName);
            return joinPoint.proceed();
        }

        TestContext context = new TestContext();
        context.setTestName(methodName);
        context.setResolvedTestConfig(resolvedConfig);
        TestContextManager.setContext(context);
        Object originalTestResult;

        try {
            System.out.println("Intercepting test method: " + methodName);
            System.out.println("Executing original test run to capture baseline...");

            originalTestResult = joinPoint.proceed();

            if (context.getOriginalResponse() == null) {
                System.out.println("No interceptable HTTP response was captured. Skipping fault simulation for this test.");
                return originalTestResult;
            }
            System.out.println("Original test run completed. Baseline response captured.");

            String endpointUrl = context.getOriginalRequest() != null ? context.getOriginalRequest().getUrl() : "";

            if (!SimulatorConfig.isTestExcluded(methodName)
                    && !SimulatorConfig.isEndpointExcluded(endpointUrl)
                    && !resolvedConfig.isEndpointExcluded(endpointUrl)) {
                Runner.executeTestWithSimulatedFaults(joinPoint, context);
            } else {
                System.out.println("Skipping fault simulation for this test due to exclusion rules.");
            }

        } finally {
            // Clean up the context for the current thread to prevent memory leaks
            // and state bleeding between tests in the same thread.
            TestContextManager.clearContext();
            System.out.println("Test method execution finished: " + methodName);
        }

        return originalTestResult;
    }

    @Around("execution(* org.apache.http.impl.client.CloseableHttpClient.execute(..))")
    public Object interceptApacheHttpClient(ProceedingJoinPoint joinPoint) throws Throwable {
        // Check if we're inside a @Test method (context exists)
        // If not (e.g., @BeforeAll, @BeforeEach), just let the request proceed normally
        if (!TestContextManager.hasContext()) {
            return joinPoint.proceed(joinPoint.getArgs());
        }

        TestContext context = TestContextManager.getContext();
        Object[] args = joinPoint.getArgs();

        HttpRequestBase httpRequest = null;
        if (args.length > 0 && args[0] instanceof HttpRequestBase) {
            httpRequest = (HttpRequestBase) args[0];

            if (context.getOriginalResponse() == null) {
                Request requestWrapper = HTTPFactory.createRequestFrom(httpRequest);
                context.setOriginalRequest(requestWrapper);
                System.out.println("Original request captured: " + requestWrapper.getUrl());
            }
        }

        // Proceed with the actual HTTP call.
        Object result = joinPoint.proceed(args);

        // Response Interception
        if (result instanceof HttpResponse) {
            HttpResponse httpResponse = (HttpResponse) result;

            // Determine current request index in this test execution
            // During baseline: use size (list is growing)
            // During simulation re-runs: use counter (list is already populated)
            int currentRequestIndex;
            if (context.getCurrentSimulationIndex() == -1) {
                // Baseline run: use size as requests are being added
                currentRequestIndex = context.getCapturedRequests().size();
            } else {
                // Simulation re-run: use counter that tracks position
                currentRequestIndex = context.getAndIncrementRequestCounter();
            }

            // If it's the baseline run (no simulation), capture ALL requests
            if (context.getCurrentSimulationIndex() == -1) {
                Response responseWrapper = HTTPFactory.createResponseFrom(httpResponse);
                httpResponse.setEntity(new StringEntity(responseWrapper.getBody()));

                // Capture FIRST request as originalResponse (for backward compatibility)
                if (context.getOriginalResponse() == null) {
                    context.setOriginalResponse(responseWrapper);
                    System.out.println("Original response captured.");
                }

                // Add to captured requests list for comprehensive simulation
                if (httpRequest != null) {
                    Request requestWrapper = HTTPFactory.createRequestFrom(httpRequest);
                    context.addCapturedRequest(requestWrapper, responseWrapper);
                    System.out.println("Captured request #" + currentRequestIndex + ": " + requestWrapper.getUrl());

                    // Log to coverage
                    Logger.parseResponse(httpRequest, context.getTestName(), responseWrapper);
                }

            } else if (context.getSimulatedResponse() != null && currentRequestIndex == context.getCurrentSimulationIndex()) {
                // During simulation run, inject mutated response for the target request
                String simulatedBody = context.getSimulatedResponse().getBody();
                httpResponse.setEntity(new StringEntity(simulatedBody));
                System.out.printf("    [RESPONSE-INJECTION] Injecting simulated response for request #%d: %s%n", currentRequestIndex, simulatedBody);
            } else {
                // For other requests during simulation, use original response
                Response responseWrapper = HTTPFactory.createResponseFrom(httpResponse);
                httpResponse.setEntity(new StringEntity(responseWrapper.getBody()));
            }
        }

        return result;
    }


    @Around("execution(* okhttp3.Call.execute(..))")
    public Object interceptOkHttpClient(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("Intercepted OkHttpClient call (placeholder, no fault injection)");
        Object result = joinPoint.proceed();

        if (result instanceof okhttp3.Response) {
            okhttp3.Response response = (okhttp3.Response) result;
            String originalResponse = response.peekBody(Long.MAX_VALUE).string();
            System.out.println("Response peeked for OkHttpClient: " + originalResponse.substring(0, Math.min(originalResponse.length(), 150)) + "...");
        }

        return result;
    }


    @Around("execution(* java.net.HttpURLConnection.connect(..))")
    public Object interceptHttpURLConnection(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("Intercepted HttpURLConnection call (placeholder, no fault injection)");
        Object result = joinPoint.proceed();
        HttpURLConnection connection = (HttpURLConnection) joinPoint.getTarget();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
        } catch (Exception e) {
        }

        return result;
    }
}