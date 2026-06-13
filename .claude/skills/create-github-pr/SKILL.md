---
name: create-github-pr
description: Create a GitHub pull request for this project using the gh CLI.
---

Create a GitHub PR using: $ARGUMENTS

1. Run `git log main..HEAD --oneline` to understand what's on the branch.
2. Abort with a message if HEAD is on `main` (nothing to PR).
3. If `$ARGUMENTS` is empty, derive title from commits; ask for description only if context is unclear.
4. Push the branch if not already pushed (`git push -u origin HEAD`).
5. Run `gh pr create --title "..." --body "..." --base main`.
6. Add `--draft` if the user mentions WIP or draft.
7. Report the created PR URL.
