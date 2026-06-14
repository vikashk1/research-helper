export const meta = {
  name: 'batch-resolve-issues',
  description: 'Resolve multiple GitHub issues in parallel worktrees',
  phases: [
    { title: 'Fetch', detail: 'Load issue details from GitHub' },
    { title: 'Fix', detail: 'One agent per issue in isolated worktree — fix, push, and open PR' },
  ],
}

const ISSUE_SCHEMA = {
  type: 'object',
  properties: {
    number: { type: 'number' },
    title: { type: 'string' },
    body: { type: 'string' },
    labels: { type: 'array', items: { type: 'string' } },
  },
  required: ['number', 'title', 'body'],
}

const FIX_SCHEMA = {
  type: 'object',
  properties: {
    fixed: { type: 'boolean' },
    summary: { type: 'string' },
    files_changed: { type: 'array', items: { type: 'string' } },
    pr_url: { type: 'string' },
  },
  required: ['fixed', 'summary'],
}

// args: array of issue numbers, e.g. [8, 9, 10]
const issueNumbers = args

phase('Fetch')
log(`Fetching ${issueNumbers.length} issues`)
const issues = await parallel(issueNumbers.map(n => () =>
  agent(
    `Run: gh issue view ${n} --json number,title,body,labels
Return the JSON object. For labels, return just the name strings.`,
    { label: `fetch-#${n}`, schema: ISSUE_SCHEMA }
  )
))
const valid = issues.filter(Boolean)
log(`Fetched ${valid.length}/${issueNumbers.length} issues`)

phase('Fix')
const fixes = await pipeline(
  valid,
  issue => {
    const isUI = (issue.labels || []).some(l => /front|ui|css|html/i.test(l))
    const agentType = isUI ? 'frontend-expert' : 'springboot-expert'
    return agent(
      `Fix GitHub issue #${issue.number}: ${issue.title}

${issue.body}

Steps:
1. Read relevant files to understand the problem.
2. Implement the fix.
3. Run mvn test -q (skip for frontend-only changes). If tests fail, attempt to fix. If still failing, set fixed=false and explain why in the summary.
4. Commit with message: fix(#${issue.number}): <short description>
5. Push the branch: git push -u origin HEAD
6. Open a PR as draft: gh pr create --title "fix(#${issue.number}): <short description>" --body "Closes #${issue.number}" --base main --draft

IMPORTANT: Only push and open a PR if ALL tests pass. If tests fail after your fix, do NOT push or open a PR — return fixed=false instead.

Return whether you fixed it, a one-line summary, files changed, and the PR URL if created.`,
      {
        label: `fix-#${issue.number}`,
        agentType,
        isolation: 'worktree',
        schema: FIX_SCHEMA,
      }
    )
  }
)

const fixed = fixes.filter(f => f && f.fixed)
const prs = fixed.filter(f => f.pr_url)
log(`Fixed ${fixed.length}/${valid.length} issues, opened ${prs.length} PRs: ${prs.map(r => r.pr_url).join(', ')}`)
return { issues: valid.length, fixed: fixed.length, prs }
