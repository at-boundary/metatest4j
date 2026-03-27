package metatest.core.interceptor;

import lombok.Data;
import metatest.core.config.ResolvedTestConfig;
import metatest.http.Request;
import metatest.http.Response;

import java.util.ArrayList;
import java.util.List;

@Data
public class TestContext {

    @Data
    public static class RequestResponsePair {
        private Request request;
        private Response response;

        public RequestResponsePair(Request request, Response response) {
            this.request = request;
            this.response = response;
        }
    }

    private String testName;
    private ResolvedTestConfig resolvedTestConfig;
    private Request originalRequest;
    private Response originalResponse;
    private Response simulatedResponse;

    // Track ALL requests/responses for comprehensive fault simulation
    private List<RequestResponsePair> capturedRequests = new ArrayList<>();
    private int currentSimulationIndex = -1; // Which request is being simulated
    private int currentRequestCounter = 0; // Counter for current request position during re-runs

    public void clearSimulation() {
        this.simulatedResponse = null;
    }

    public void resetRequestCounter() {
        this.currentRequestCounter = 0;
    }

    public int getAndIncrementRequestCounter() {
        return currentRequestCounter++;
    }

    public void addCapturedRequest(Request request, Response response) {
        capturedRequests.add(new RequestResponsePair(request, response));
    }

}
