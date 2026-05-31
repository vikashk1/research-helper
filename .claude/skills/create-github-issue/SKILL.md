---
name: create-github-issue
description: Create a GitHub issue for this project using the gh CLI.
---

Create a GitHub issue using: $ARGUMENTS

1. If `$ARGUMENTS` is empty, ask for title and body.
2. Run `gh issue create --title "..." --body "..."` in the repo root.
3. If labels or assignees are mentioned, add `--label` / `--assignee` flags.
4. Report the created issue URL.
