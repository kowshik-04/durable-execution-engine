package engine;

public class StepRecord {

    private final String workflowId;
    private final String stepKey;
    private final StepStatus status;
    private final String output; // JSON string (nullable)

    public StepRecord(String workflowId,
                      String stepKey,
                      StepStatus status,
                      String output) {
        this.workflowId = workflowId;
        this.stepKey = stepKey;
        this.status = status;
        this.output = output;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getStepKey() {
        return stepKey;
    }

    public StepStatus getStatus() {
        return status;
    }

    public String getOutput() {
        return output;
    }
}
