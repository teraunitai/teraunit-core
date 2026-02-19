# TeraUnit (Alpha)

**Status:** Phase-5 Closed Alpha (Red Team)

> **Disclaimer: No free compute.**
> TeraUnit is a **Bring-Your-Own-Key (BYOK)** orchestrator.
> You are billed directly by your provider (Lambda / RunPod / Vast) at their standard rates.
> This project is orchestration + safety tooling only.

## The problem: zombie instances

GPU instances can keep running (and billing) after the control plane loses contact (agent crash, networking/DNS issues, bad auth token, etc.).

TeraUnit adds a safety harness:

- Instances periodically send authenticated check-ins to the control plane.
- If check-ins stop for long enough, the control plane terminates the instance to stop the billing clock.

## What it does (today)

- Launch GPU instances on supported providers (BYOK)
- List active instances
- Terminate instances manually
- Automatically terminates instances that go silent ("zombie" protection)
- Optional hard stop lease (max runtime) to prevent surprise weekend spend

## Key handling (accurate)

- **Encrypted at rest:** provider API keys are stored using **AES-GCM**.
- **Purpose-bound:** keys are persisted solely so the server can terminate instances later (reaper/manual stop) even if your browser/session closes.
- **No logging/UI:** keys are not meant to be logged or displayed.

## Access model (Phase 5)

The hosted control plane endpoints are gated by a control token.

- Provide the token via `X-Tera-Control-Token` header (or `Authorization: Bearer ...`).
- Without the token, `/v1/launch` and `/v1/instances/*` are blocked.

## Local run (dev)

Requirements:

- Java 21 (ensure `java -version` shows 21; set `JAVA_HOME` accordingly)
- Maven
- Postgres
- Redis

Environment variables (common):

- `TERA_CONTROL_TOKEN`
- `TERA_VAULT_KEY` (base64-encoded AES key material)
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL`
- `JDBC_DATABASE_URL`, `JDBC_DATABASE_USERNAME`, `JDBC_DATABASE_PASSWORD`
- `TERA_CALLBACK_URL`
- `TERA_MAX_RUNTIME_MINUTES`

Run:

```bash
./mvnw spring-boot:run

# Windows PowerShell:
.\mvnw.cmd spring-boot:run
```

## Notes

This is a closed alpha. Expect breaking changes.
