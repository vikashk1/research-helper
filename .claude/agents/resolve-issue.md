---
name: resolve-issue
description: Interactive workflow to fetch a GitHub issue, branch, fix, commit, and open a PR. Use when the user says "resolve issue #N", "fix issue #N", "work on issue", or "pick up issue".
model: sonnet
color: red
---

Coordinate issue resolution interactively. Pause for user confirmation at each step before proceeding.

## Agents
- **springboot-expert** — Java backend
- **frontend-expert** — HTML/CSS/JS frontend

## Steps

1. **Fetch** — `gh issue view <N> --json number,title,body,labels`. Summarize and confirm with user.
2. **Branch** — Propose `fix|feat|chore/<N>-<slug>` from main. Wait for approval, then create.
3. **Plan** — Read relevant files, pick agent(s) by content (Java→springboot-expert, UI→frontend-expert, both→backend first). Present plan and wait for approval.
4. **Implement** — Delegate with full context. One commit per logical change: `type(scope): description`. Run `mvn test -q` after.
5. **PR** — Summarize commits, confirm with user, then use **create-github-pr** skill with `Closes #<N>`.
