---
name: create-github-issue
description: Create a GitHub issue for this project using the gh CLI.
---

Create a GitHub issue using: $ARGUMENTS

1. If `$ARGUMENTS` is empty, ask for title and body.
2. Warn if on `main` branch — issues usually relate to feature/fix branches.
3. Run `gh issue create --title "..." --body "..."`.
4. Add `--label` / `--assignee` flags if mentioned.
5. Report the created issue URL.
