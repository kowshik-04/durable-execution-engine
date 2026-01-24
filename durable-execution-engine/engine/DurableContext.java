package engine;

import java.util.Optional;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DurableContext {

    private final String workflowId;
    private final DurableStore store;
    private final SequenceGenerator sequence;
    private final StepExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DurableContext(String workflowId, DurableStore store) {
        this.workflowId = workflowId;
        this.store = store;
        int lastSeq = store.getMaxSequenceForWorkflow(workflowId);
        this.sequence = new SequenceGenerator(lastSeq);

        this.executor = new StepExecutor(store, objectMapper);
    }

    public <T> T step(String stepId, Callable<T> fn) {
        int seq = sequence.next();
        String stepKey = stepId + "-" + seq;

        // 1. Read durable state ONLY
        Optional<StepRecord> existing = store.getStep(workflowId, stepKey);

        if (existing.isPresent()) {
            StepRecord record = existing.get();

            if (record.getStatus() == StepStatus.COMPLETED) {
                return deserialize(record.getOutput());
            }

            if (record.getStatus() == StepStatus.RUNNING) {
                // zombie: mark failed and retry with NEW sequence
                store.markStepFailed(workflowId, stepKey);
                return step(stepId, fn); // retry with new sequence
            }

            // FAILED → retry with new sequence
            return step(stepId, fn);
        }

        // 2. Single execution authority
        return executor.execute(workflowId, stepKey, fn);
    }


    @SuppressWarnings("unchecked")
    private <T> T deserialize(String json) {
        try {
            if (json == null) {
                return null;
            }
            return (T) objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize step output", e);
        }
    }
}
