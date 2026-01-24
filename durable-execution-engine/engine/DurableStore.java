package engine;

import java.util.Optional;

public interface DurableStore {

    Optional<StepRecord> getStep(String workflowId, String stepKey);

    void insertRunningStep(String workflowId, String stepKey);

    void markStepCompleted(String workflowId, String stepKey, String output);

    void markStepFailed(String workflowId, String stepKey);
    
    int getMaxSequenceForWorkflow(String workflowId);

}
