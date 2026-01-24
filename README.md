# Native Durable Execution Engine

A crash-safe, durable workflow runner written in Java 21 with no external orchestrator, DSL, or cloud dependencies. Steps persist to SQLite so workflows resume deterministically after crashes while preserving at-most-once semantics.

## Table of Contents
- [Why This Exists](#why-this-exists)
- [Architecture](#architecture)
- [Engine Guarantees](#engine-guarantees)
- [Data Model](#data-model)
- [Execution Flow](#execution-flow)
- [CLI Walkthrough](#cli-walkthrough)
- [Local Setup](#local-setup)
- [Project Layout](#project-layout)
- [Extending the Engine](#extending-the-engine)
- [Troubleshooting](#troubleshooting)
- [Roadmap](#roadmap)

## Why This Exists
Demonstrates a transparent, correct-by-construction workflow engine that favors simplicity over magic while still delivering:
- Durable, idempotent steps stored in SQLite
- Crash-safe resume with zombie-step detection
- Parallel execution via `CompletableFuture`
- Deterministic sequencing to avoid duplicate effects
- Interactive CLI that can intentionally crash to prove correctness

## Architecture
```
┌───────────────┐      ┌────────────────┐      ┌────────────────────┐
│ CLI (Main)    │ ---> │ DurableContext │ ---> │ StepExecutor       │
│ app/Main.java │      │ workflowId     │      │ inserts RUNNING    │
└───────┬───────┘      │ step() API     │      │ runs user code     │
        │              └──────┬─────────┘      │ persists COMPLETED │
        │                     │                └─────────┬──────────┘
        │                     v                          │
        │              ┌───────────────┐                 │
        └────────────> │ DurableStore  │ <────────────────┘
                       │ SQLite (single conn, sync writes)
                       └───────────────┘
```

## Engine Guarantees
- At-most-once execution per logical step ID and sequence number
- Crash-safe recovery: RUNNING steps on crash become FAILED and are retried with a new sequence
- Completed steps are skipped on replay; outputs are reused
- Deterministic sequencing prevents primary key conflicts when reusing step IDs
- Thread-safe persistence (single connection; synchronized writes) to avoid SQLITE_BUSY

## Data Model
SQLite `steps` table:
```
CREATE TABLE steps (
  workflow_id TEXT,
  step_key    TEXT,   -- stepId + "-" + sequenceNumber
  status      TEXT,   -- RUNNING | COMPLETED | FAILED
  output      TEXT,
  PRIMARY KEY (workflow_id, step_key)
);
```
- `workflow_id` persists in `workflow.id` so a resume uses the same identity.
- Sequence numbers are restored on startup to maintain deterministic ordering.

## Execution Flow
1) `step()` checks SQLite for the latest status for the `stepId` prefix.
  This prefix lookup lets the engine find completed or zombie executions even when a step reruns with a new sequence number.
2) If status is COMPLETED → skip and reuse output. RUNNING → mark zombie, retry. FAILED → retry.
3) Insert `(workflowId, stepKey, RUNNING)`.
4) Execute user function; persist `(COMPLETED, output)` or `(FAILED)` on exception.
5) Parallel steps run with `CompletableFuture`; SQLite writes remain serialized.

## CLI Walkthrough
Build and run the shaded JAR (Java 21, Maven 3.9+):
```bash
mvn clean package
java -jar target/durable-execution-engine-1.0-SNAPSHOT.jar
```
Menu options (from app/Main.java):
1) Start workflow from beginning — deletes prior state, runs clean.
2) Resume existing workflow — reuses `workflow.id` and DB state.
3) Start NEW workflow and simulate crash — pick a step ID to crash at; restart with option 2 to observe recovery.
4) View workflow state — prints `step_key` and `status` from SQLite.
5) Exit.

Example crash demo:
- Choose option 3, crash at "Provision Laptop".
- Process exits mid-run; a step remains RUNNING.
- Restart and choose option 2; engine marks zombie as FAILED and reruns it with a new sequence number.

## Local Setup
- Requirements: Java 21+, Maven 3.9+, SQLite (bundled via sqlite-jdbc).
- Build: `mvn clean package` (produces shaded JAR at `target/durable-execution-engine-1.0-SNAPSHOT.jar`).
- Run: `java -jar target/durable-execution-engine-1.0-SNAPSHOT.jar`.
- Tests: `mvn test` (JUnit 5; add cases under `**/*Test.java`).

## Project Layout
- app/ — CLI entrypoint and crash simulator.
- engine/ — core engine (DurableContext, SqliteDurableStore, StepExecutor, StepRecord, StepStatus).
- examples/onboarding/ — EmployeeOnboardingWorkflow demonstrating sequential + parallel steps.
- db/schema.sql — schema reference; SQLite DB materializes as `engine.db` at runtime.

## Extending the Engine
- Create a workflow: implement a class that accepts `DurableContext ctx` and call `ctx.step("logicalName", fn)` for each step.
- Parallelism: wrap independent `ctx.step` invocations in `CompletableFuture.runAsync` and `allOf().join()`.
- Persistence: outputs are serialized JSON (Jackson). Use small DTOs or Strings for clarity.
- Portability: swap `SqliteDurableStore` with another `DurableStore` implementation (e.g., Postgres) using the same contract.

## Troubleshooting
- Stuck workflow? Delete `engine.db` and `workflow.id` to start fresh (options 1 or 3 already do this).
- Seeing SQLITE_BUSY? The engine uses a single connection and synchronized writes; concurrent external writers can still contend.
- Need to inspect state? Choose menu option 4 or open `engine.db` with `sqlite3` and query `steps`.

## Roadmap
- Pluggable locks for multi-node execution
- Backoff/retry policies per step
- Deadline and timeout handling
- Metrics hooks and structured logging

This project showcases how to build a durable, crash-safe workflow runner using only native Java and SQLite while keeping the behavior transparent and auditable.
