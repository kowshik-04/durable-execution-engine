package app;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import engine.DurableContext;
import engine.SqliteDurableStore;
import engine.StepStatus;
import examples.onboarding.EmployeeOnboardingWorkflow;

public class Main {

    // Used ONLY for CLI narration (NOT step identity)
    private static final AtomicInteger STEP_COUNTER = new AtomicInteger(0);

    // Crash is now defined by STEP ID (not step number)
    private static String CRASH_AT_STEP_ID = null;

    public static void main(String[] args) throws Exception {

        printHeader();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            printMenu();
            int choice = scanner.nextInt();

            switch (choice) {
                case 1 -> startFreshWorkflow();
                case 2 -> resumeWorkflow();
                case 3 -> crashWorkflow(scanner);
                case 4 -> showWorkflowState();
                case 5 -> {
                    System.out.println("------ Exiting.....!------");
                    System.exit(0);
                }
                default -> System.out.println("❌ Invalid choice");
            }
        }
    }

    // ---------------- MENU ACTIONS ----------------

    // Start a brand-new workflow normally
    private static void startFreshWorkflow() throws Exception {
        deleteState();
        System.out.println("▶ Starting NEW workflow from beginning...\n");
        runWorkflow(false);
    }

    // Resume only if a workflow exists
    private static void resumeWorkflow() throws Exception {
        if (!new File("workflow.id").exists()) {
            System.out.println("❌ No workflow to resume. Start a new workflow first.\n");
            return;
        }
        System.out.println("🔁 Resuming existing workflow...\n");
        runWorkflow(false);
    }

    // Always start a NEW workflow and crash by STEP ID
    private static void crashWorkflow(Scanner scanner) throws Exception {

        // 🚨 Always start fresh for crash demo
        deleteState();

        System.out.println("""
                Where would you like to simulate a crash?

                1. Create Employee
                2. Provision Laptop
                3. Provision Access
                4. Send Welcome Email
                """);

        System.out.print("Enter step number to crash at: ");
        int choice = scanner.nextInt();

        CRASH_AT_STEP_ID = switch (choice) {
            case 1 -> "createEmployee";
            case 2 -> "provisionLaptop";
            case 3 -> "provisionAccess";
            case 4 -> "sendWelcomeEmail";
            default -> null;
        };

        if (CRASH_AT_STEP_ID == null) {
            System.out.println("❌ Invalid step choice\n");
            return;
        }

        System.out.println("\n⚠️ A NEW workflow will crash at step: "
                + CRASH_AT_STEP_ID + "\n");

        runWorkflow(true);
    }

    // ---------------- CORE WORKFLOW RUNNER ----------------

    private static void runWorkflow(boolean crashEnabled) throws Exception {

        File workflowFile = new File("workflow.id");
        String workflowId;

        if (workflowFile.exists()) {
            workflowId = Files.readString(workflowFile.toPath()).trim();
            System.out.println("🔁 Workflow ID: " + workflowId);
        } else {
            workflowId = UUID.randomUUID().toString();
            Files.writeString(workflowFile.toPath(), workflowId);
            System.out.println("🆔 New Workflow ID: " + workflowId);
        }

        SqliteDurableStore store = new SqliteDurableStore();

        DurableContext ctx = new DurableContext(workflowId, store) {
            @Override
            public <T> T step(String stepId, java.util.concurrent.Callable<T> fn) {

                int stepNo = STEP_COUNTER.incrementAndGet();
                String stepKeyPrefix = stepId + "-";

                StepStatus previousStatus =
                        findLatestStepStatus(workflowId, stepKeyPrefix);

                // ---------- CLI narration ----------
                if (previousStatus == StepStatus.COMPLETED) {
                    System.out.println("⏭ STEP " + stepNo + ": "
                            + humanize(stepId) + " (already completed, skipping)");
                } else if (previousStatus == StepStatus.RUNNING) {
                    System.out.println("⚠️ STEP " + stepNo + ": "
                            + humanize(stepId) + " (zombie detected, retrying)");
                } else {
                    System.out.println("▶ STEP " + stepNo + ": " + humanize(stepId));
                }

                // ---------- CRASH SIMULATION (BY STEP ID) ----------
                if (crashEnabled
                        && CRASH_AT_STEP_ID != null
                        && stepId.equals(CRASH_AT_STEP_ID)) {

                    System.out.println("💥 CRASH SIMULATED AT STEP: "
                            + humanize(stepId));
                    System.out.println("ℹ️ Other parallel steps may still finish.");
                    System.out.println("Restart the app and choose RESUME.\n");
                    System.exit(1);
                }

                // ---------- Actual engine execution ----------
                T result = super.step(stepId, fn);

                if (previousStatus != StepStatus.COMPLETED) {
                    System.out.println("✔ STEP " + stepNo + " COMPLETED\n");
                }

                return result;
            }
        };

        new EmployeeOnboardingWorkflow(ctx).run();

        System.out.println("🎉 Workflow finished successfully!\n");

        STEP_COUNTER.set(0);
        CRASH_AT_STEP_ID = null;
    }

    // ---------------- STATE VIEW ----------------

    private static void showWorkflowState() {
        File db = new File("engine.db");
        if (!db.exists()) {
            System.out.println("❌ No workflow state found.\n");
            return;
        }

        System.out.println("\n📊 Workflow State (from SQLite)");
        System.out.println("--------------------------------");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:engine.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT step_key, status FROM steps ORDER BY step_key")) {

            while (rs.next()) {
                System.out.printf("• %-25s : %s%n",
                        rs.getString("step_key"),
                        rs.getString("status"));
            }
        } catch (Exception e) {
            System.out.println("❌ Failed to read DB");
        }

        System.out.println();
    }

    // ---------------- HELPERS ----------------

    private static StepStatus findLatestStepStatus(
            String workflowId,
            String stepKeyPrefix) {

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:engine.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT status FROM steps " +
                     "WHERE workflow_id = '" + workflowId + "' " +
                     "AND step_key LIKE '" + stepKeyPrefix + "%' " +
                     "ORDER BY step_key DESC LIMIT 1")) {

            if (rs.next()) {
                return StepStatus.valueOf(rs.getString("status"));
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static void deleteState() throws Exception {
        Files.deleteIfExists(new File("engine.db").toPath());
        Files.deleteIfExists(new File("workflow.id").toPath());
    }

    private static String humanize(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1 $2").toUpperCase();
    }

    private static void printHeader() {
        System.out.println("""
                ========================================
                🚀 Durable Execution Engine – Demo CLI
                ========================================
                This demo shows:
                ✔ Crash-safe workflows
                ✔ Durable steps (SQLite)
                ✔ Parallel execution
                ✔ Resume without re-running steps
                ----------------------------------------
                """);
    }

    private static void printMenu() {
        System.out.println("""
                Choose an option:

                1️⃣  Start workflow from beginning
                2️⃣  Resume existing workflow
                3️⃣  Start NEW workflow and simulate crash
                4️⃣  View workflow state
                5️⃣  Exit

                Enter choice:
                """);
    }
}
