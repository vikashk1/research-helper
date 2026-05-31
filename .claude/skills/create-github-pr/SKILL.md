---
name: create-github-pr
description: Create a GitHub pull request for this project using the gh CLI.
---

Create a GitHub PR using: $ARGUMENTS

1. Run `git status` and `git log main..HEAD --oneline` to understand what's on the branch.
2. If `$ARGUMENTS` is empty, derive title from commits; ask for description only if context is unclear.
3. Push the branch if not already pushed (`git push -u origin HEAD`).
4. Run `gh pr create --title "..." --body "..." --base main`.
5. Add `--draft` if the user mentions WIP or draft.
6. Report the created PR URL.
