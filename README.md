# TeraUnit Core

TeraUnit is a bring-your-own-provider-key control plane for launching, listing, and terminating GPU instances across multiple providers.

This repo contains the Spring Boot backend (`teraunit-core`). It is intended for a closed alpha where access to the hosted control plane is gated.

## What it does (today)

- Launch GPU instances on supported providers (BYO-key)
- List active instances
- Terminate instances manually
- Automatically terminates instances that go silent ("zombie" protection)
- Optional hard stop lease (max runtime) to prevent surprise weekend spend

## Key handling (accurate)

- Provider API keys are stored **AES-GCM encrypted at rest**.
- Keys are stored solely so the server can terminate instances later (reaper/manual stop) even if the browser closes.
- Keys are never meant to be logged or displayed.

## Access model (Phase 5)

The control plane endpoints are gated by a control token.

- Provide the token via `X-Tera-Control-Token` header (or `Authorization: Bearer ...`).
- Without the token, `/v1/launch` and `/v1/instances/*` are blocked.

## Local run (minimal)

Requirements:
- Java 21
- Maven
- Postgres (or set JPA to a local dev setup)
- Redis (for offer cache)

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
```

## Notes

This is a closed alpha. Expect breaking changes.
