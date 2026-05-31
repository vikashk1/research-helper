---
name: review-github-pr
description: Review a GitHub PR and post inline comments using the gh CLI.
---

Review PR: $ARGUMENTS (PR number or URL; if empty, use the current branch's open PR)

## Setup
1. Read `DESIGN.md` for architectural context before reviewing.
2. Fetch the diff: `gh pr diff <pr>`
3. Fetch metadata: `gh pr view <pr> --json title,body,headRefName,baseRefName`

## What to check

**Correctness**
- Logic errors, off-by-one, null/empty not handled at boundaries
- Async/concurrency issues (`@Async` methods, shared state, SSE emitters)
- JPA pitfalls: missing transactions, N+1 queries, lazy-load outside session

**Architecture**
- Violates layering from DESIGN.md (e.g. agent logic leaking into controller)
- New agent not following the coordinator pattern described in DESIGN.md
- Business logic in controller instead of service

**Security**
- User input reaching shell commands, SQL, or log output unsanitized
- Secrets or credentials in code

**Maintainability**
- Method doing more than one thing; hard-to-follow control flow
- Magic strings/numbers that should be constants
- Exception swallowed silently

**Skip:** formatting, import order, Javadoc, style preferences.

## Posting comments
For each finding, post an inline comment:
```
gh api repos/{owner}/{repo}/pulls/<pr>/comments \
  --method POST \
  -f body="..." \
  -f commit_id="<head_sha>" \
  -f path="<file>" \
  -F line=<line>
```
Get `head_sha`: `gh pr view <pr> --json headRefOid -q .headRefOid`
Get `{owner}/{repo}`: `gh repo view --json nameWithOwner -q .nameWithOwner`

## Wrap up
Post a summary: `gh pr review <pr> --comment -b "..."`
Use `--approve` or `--request-changes` only if the user explicitly asks.
Report total comments posted and the PR URL.
