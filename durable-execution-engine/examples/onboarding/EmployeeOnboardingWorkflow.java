package examples.onboarding;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import engine.DurableContext;

public class EmployeeOnboardingWorkflow {

    private final DurableContext ctx;

    public EmployeeOnboardingWorkflow(DurableContext ctx) {
        this.ctx = ctx;
    }

    public void run() {

        // Step 1: Create employee record
        String employeeId = ctx.step("createEmployee", () -> {
            log("Creating employee record");
            sleep(2000);
            log("Employee record created");
            return "EMP-" + System.currentTimeMillis();
        });

        // Step 2: Parallel provisioning
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CompletableFuture<Void> laptopFuture =
                CompletableFuture.runAsync(() ->
                        ctx.step("provisionLaptop", () -> {
                            log("Provisioning laptop for " + employeeId);
                            sleep(3000);
                            log("Laptop provisioned");
                            return null;
                        }), executor
                );

        CompletableFuture<Void> accessFuture =
                CompletableFuture.runAsync(() ->
                        ctx.step("provisionAccess", () -> {
                            log("Provisioning system access for " + employeeId);
                            sleep(2500);
                            log("System access provisioned");
                            return null;
                        }), executor
                );

        CompletableFuture.allOf(laptopFuture, accessFuture).join();
        executor.shutdown();

        // Step 3: Send welcome email
        ctx.step("sendWelcomeEmail", () -> {
            log("Sending welcome email to " + employeeId);
            sleep(1500);
            log("Welcome email sent");
            return null;
        });

        log("Employee onboarding workflow completed");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String message) {
        System.out.println("[Workflow] " + message);
    }
}
