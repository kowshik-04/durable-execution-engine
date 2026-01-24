package engine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class SqliteDurableStore implements DurableStore {

    private static final String DB_URL = "jdbc:sqlite:engine.db";
    private final Connection connection;

    public SqliteDurableStore() {
        try {
            // Force SQLite JDBC driver to load
            Class.forName("org.sqlite.JDBC");

            this.connection = DriverManager.getConnection(DB_URL);
            initializeSchema();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SQLite store", e);
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS steps (
                  workflow_id TEXT NOT NULL,
                  step_key TEXT NOT NULL,
                  status TEXT NOT NULL,
                  output TEXT,
                  PRIMARY KEY (workflow_id, step_key)
                )
            """);
        }
    }

    @Override
    public synchronized Optional<StepRecord> getStep(String workflowId, String stepKey) {
        String sql = """
            SELECT status, output
            FROM steps
            WHERE workflow_id = ? AND step_key = ?
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setString(2, stepKey);

            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return Optional.empty();
            }

            StepStatus status = StepStatus.valueOf(rs.getString("status"));
            String output = rs.getString("output");

            return Optional.of(
                new StepRecord(workflowId, stepKey, status, output)
            );

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch step", e);
        }
    }

    @Override
    public synchronized void insertRunningStep(String workflowId, String stepKey) {
        String sql = """
            INSERT INTO steps (workflow_id, step_key, status)
            VALUES (?, ?, ?)
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setString(2, stepKey);
            ps.setString(3, StepStatus.RUNNING.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert RUNNING step", e);
        }
    }

    @Override
    public synchronized void markStepCompleted(
            String workflowId,
            String stepKey,
            String output) {

        String sql = """
            UPDATE steps
            SET status = ?, output = ?
            WHERE workflow_id = ? AND step_key = ?
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, StepStatus.COMPLETED.name());
            ps.setString(2, output);
            ps.setString(3, workflowId);
            ps.setString(4, stepKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark step COMPLETED", e);
        }
    }

    @Override
    public synchronized void markStepFailed(String workflowId, String stepKey) {
        String sql = """
            UPDATE steps
            SET status = ?
            WHERE workflow_id = ? AND step_key = ?
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, StepStatus.FAILED.name());
            ps.setString(2, workflowId);
            ps.setString(3, stepKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark step FAILED", e);
        }
    }

    // 🔑 CRITICAL: sequence continuity across restarts
    @Override
    public synchronized int getMaxSequenceForWorkflow(String workflowId) {
        String sql = "SELECT step_key FROM steps WHERE workflow_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ResultSet rs = ps.executeQuery();

            int max = 0;
            while (rs.next()) {
                String stepKey = rs.getString("step_key");
                int idx = stepKey.lastIndexOf("-");
                if (idx != -1) {
                    int seq = Integer.parseInt(stepKey.substring(idx + 1));
                    max = Math.max(max, seq);
                }
            }
            return max;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to read max sequence", e);
        }
    }
}
