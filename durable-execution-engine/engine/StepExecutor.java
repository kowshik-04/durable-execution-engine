package engine;

import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;

public class StepExecutor {

    private final DurableStore store;
    private final ObjectMapper objectMapper;

    public StepExecutor(DurableStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public <T> T execute(String workflowId,
                         String stepKey,
                         Callable<T> fn) {

        // Insert RUNNING before side effect
        store.insertRunningStep(workflowId, stepKey);

        try {
            // Execute user logic
            T result = fn.call();

            // Serialize output
            String output = objectMapper.writeValueAsString(result);

            // Persist success
            store.markStepCompleted(workflowId, stepKey, output);

            return result;

        } catch (Exception e) {
            // Persist failure
            store.markStepFailed(workflowId, stepKey);
            throw new RuntimeException("Step execution failed: " + stepKey, e);
        }
    }
}
