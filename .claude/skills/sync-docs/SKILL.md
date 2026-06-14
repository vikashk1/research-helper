---
name: sync-docs
description: Regenerate docs/api-reference.md from controller source code and add missing @Operation annotations.
---

Sync API documentation from source code: $ARGUMENTS

## Steps

1. **Find controllers.** Glob `src/main/java/**/*Controller.java`. Read each file.

2. **Regenerate `docs/api-reference.md`.** Build a concise endpoint table (method, path, description) plus Job fields and SSE notes. Keep it short — no request/response body schemas. Overwrite the existing file.

3. **Check Swagger annotations.** For each endpoint method missing `@Operation(summary="...")`, add one. Import `io.swagger.v3.oas.annotations.Operation` if not already imported.

4. **Report.** List what changed: endpoints added/removed from the reference, annotations added.
