# API Reference

Base path: `/api/jobs`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/jobs/clarify` | Generate clarifying questions for a topic |
| `POST` | `/api/jobs` | Create a job and start the research pipeline |
| `GET` | `/api/jobs` | List all jobs (newest first) |
| `GET` | `/api/jobs/{id}` | Get a single job |
| `POST` | `/api/jobs/{id}/restart` | Restart a failed job |
| `GET` | `/api/jobs/{id}/stream` | SSE stream of live log lines |

## Job fields

`id`, `topic`, `clarificationAnswers`, `status`, `report` (Markdown), `errorMessage`, `createdAt`, `updatedAt`

**Status values:** `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`

## SSE stream

`GET /api/jobs/{id}/stream` — each event is a plain text log line. Stream closes when the job completes or fails.
