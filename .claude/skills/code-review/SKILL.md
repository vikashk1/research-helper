---
name: code-review
description: Review code for correctness, security, design, and simplicity. Works on any target — a file, a module, a branch diff, or a PR diff.
---

Review code: $ARGUMENTS (file path, glob pattern, branch diff like `main..HEAD`, or PR number; if empty, review staged changes)

## Resolve target
1. If `$ARGUMENTS` is a PR number: `gh pr diff <N>`
2. If `$ARGUMENTS` is a branch diff (e.g. `main..HEAD`): `git diff $ARGUMENTS`
3. If `$ARGUMENTS` is a file/glob: read the file(s) directly
4. If empty: `git diff --cached` (staged changes)

## What to check

**Correctness**
- Logic errors, off-by-one, null/empty not handled at boundaries
- Async/concurrency issues (`@Async` methods, shared state, SSE emitters)
- JPA pitfalls: missing transactions, N+1 queries, lazy-load outside session

**Architecture**
- Violates layering (e.g. agent logic leaking into controller)
- Business logic in controller instead of service
- New agent not following the coordinator pattern

**Security**
- User input reaching shell commands, SQL, or log output unsanitized
- Secrets or credentials in code

**Simplicity**
- Unnecessary abstraction, dead code, over-engineering
- Method doing more than one thing; hard-to-follow control flow
- Magic strings/numbers that should be constants
- Exception swallowed silently

**Skip:** formatting, import order, Javadoc, style preferences.

## Output format
For each finding:
```
[severity] file:line — description and fix
```
Severity: `bug`, `security`, `design`, `nit`

End with a summary: total findings by severity, overall assessment (clean / minor issues / needs work).
