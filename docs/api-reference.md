# API Reference

Base path: `/api/jobs`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/jobs/clarify` | Generate clarifying questions for a topic |
| `POST` | `/api/jobs` | Create a job and start the research pipeline |
| `GET` | `/api/jobs` | List all jobs (newest first) |
| `GET` | `/api/jobs/{id}` | Get a single job |
| `POST` | `/api/jobs/{id}/restart` | Restart a failed job |
| `DELETE` | `/api/jobs/{id}` | Delete a single job (204 NO_CONTENT) |
| `DELETE` | `/api/jobs/completed` | Delete all completed jobs; returns `{"deleted": N}` |
| `GET` | `/api/jobs/{id}/stream` | SSE stream of live log lines |

## Job fields

`id`, `topic`, `clarificationAnswers`, `status`, `report` (Markdown), `errorMessage`, `createdAt`, `updatedAt`

**Status values:** `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`

## JobResponseDto extra fields (GET /api/jobs/{id} only)

| Field | Type | Description |
|-------|------|-------------|
| `stages` | `JobStageDto[]` | Per-pipeline-stage progress records |
| `totalInputTokens` | `long` | Accumulated input tokens across all agent calls |
| `totalOutputTokens` | `long` | Accumulated output tokens across all agent calls |
| `modelId` | `string` | Server-configured model ID (e.g. `claude-haiku-4-5`) |

The frontend uses `modelId` to look up per-token pricing in a client-side table and renders an estimated cost badge on the report view.

## SSE stream

`GET /api/jobs/{id}/stream` — each event is a plain text log line. Stream closes when the job completes or fails.
