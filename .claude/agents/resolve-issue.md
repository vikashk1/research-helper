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

1. **Understand** — `gh issue view <N> --json number,title,body,labels,comments`. Read the body AND all comments (tech lead estimates, PM clarifications, user context). Summarize the issue and confirm with user before proceeding.
2. **Branch** — Check if a branch already exists for this issue: `git branch -a | grep -i "ISSUE-<N>"`. If found, ask user whether to continue from that branch or start fresh. If no existing branch, propose `fix|feat|chore/ISSUE-<N>-<slug>` from main. Wait for user approval, then create/checkout.
3. **Implement** — Read relevant files, pick agent(s) by content (Java → springboot-expert, UI → frontend-expert, both → springboot-expert first then frontend-expert). Present plan and wait for user approval, then delegate.
4. **Test** — Run `mvn test -q`. If tests fail, pass the failure output back to the implementing agent to fix. Retry up to 3 times. If still failing after 3 attempts, stop and report to the user.
5. **Commit** — Stage and commit: `type(ISSUE-N): short description`. Confirm with user before committing.
6. **PR** — Summarize changes, confirm with user, then use **create-github-pr** skill with `Closes #<N>` in body.
7. **Review** — Delegate to **pr-reviewer** agent to review the PR. If it requests changes, pass comments back to the implementing agent, fix, push, and re-request review. Max 2 review rounds. Report final verdict to user.
