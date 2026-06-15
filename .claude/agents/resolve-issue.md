---
name: resolve-issue
description: Interactive workflow to fetch a GitHub issue, branch, fix, commit, and open a PR. Use when the user says "resolve issue #N", "fix issue #N", "work on issue", or "pick up issue".
model: sonnet
color: red
---


## Agents
- **springboot-expert** — Java backend
- **frontend-expert** — HTML/CSS/JS frontend
- **qa-engineer** — test execution, acceptance criteria validation, UI verification

## Steps

1. **Understand** — `gh issue view <N> --json number,title,body,labels,comments`. Read the body AND all comments (tech lead estimates, PM clarifications, user context). Summarize the issue briefly and proceed.
2. **Branch** — Check if a branch already exists: `git branch -a | grep -i "ISSUE-<N>"`. If found, continue from it. If not, create `fix|feat|chore/ISSUE-<N>-<slug>` from main and checkout.
3. **Implement** — Read relevant files, pick agent(s) by content (Java → springboot-expert, UI → frontend-expert, both → springboot-expert first then frontend-expert). Delegate immediately.
4. **Unit Test** — Run `mvn test -q`. If tests fail, pass the failure output back to the implementing agent to fix. Retry up to 3 times. If still failing after 3 attempts, stop and report to the user.
5. **Commit** — Stage and commit: `type(ISSUE-N): short description`.
6. **PR** — Use **create-github-pr** skill with `Closes #<N>` in body.
7. **QA Validation** — Delegate to **qa-engineer** to validate against acceptance criteria, check edge cases, and verify UI if applicable. If it fails, pass details back to the implementing agent to fix, push, and re-validate. Max 2 rounds.
8. **Code Review** — Delegate to **pr-reviewer**. If it requests changes, pass comments back to the implementing agent, fix, push, and re-request review. Max 2 review rounds. Report final verdict to user.
