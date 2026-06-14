export const meta = {
  name: 'full-sdlc',
  description: 'End-to-end SDLC: prioritize, estimate, implement, test, review, ship',
  phases: [
    { title: 'Prioritize', detail: 'PM creates GitHub issues with acceptance criteria' },
    { title: 'Estimate', detail: 'Tech lead sizes issues and picks approach' },
    { title: 'Implement', detail: 'Dev agents fix issues in isolated worktrees' },
    { title: 'Test', detail: 'QA validates against acceptance criteria' },
    { title: 'Review', detail: 'Code reviewer gates merge readiness' },
    { title: 'Ship', detail: 'Final PR URLs ready for human merge' },
  ],
}

const ISSUE_SCHEMA = {
  type: 'object',
  properties: {
    number: { type: 'number' },
    title: { type: 'string' },
    labels: { type: 'array', items: { type: 'string' } },
    ac: { type: 'array', items: { type: 'string' } },
  },
  required: ['number', 'title', 'ac'],
}

const ESTIMATE_SCHEMA = {
  type: 'object',
  properties: {
    issue: { type: 'number' },
    size: { type: 'string', enum: ['S', 'M', 'L', 'XL'] },
    approach: { type: 'string' },
    risks: { type: 'array', items: { type: 'string' } },
    should_split: { type: 'boolean' },
  },
  required: ['issue', 'size', 'approach', 'should_split'],
}

const FIX_SCHEMA = {
  type: 'object',
  properties: {
    fixed: { type: 'boolean' },
    pr_url: { type: 'string' },
    summary: { type: 'string' },
  },
  required: ['fixed', 'summary'],
}

const QA_SCHEMA = {
  type: 'object',
  properties: {
    passed: { type: 'boolean' },
    details: { type: 'string' },
  },
  required: ['passed', 'details'],
}

const REVIEW_SCHEMA = {
  type: 'object',
  properties: {
    approved: { type: 'boolean' },
    comments_posted: { type: 'number' },
    summary: { type: 'string' },
  },
  required: ['approved', 'summary'],
}

// --- Phase 1: Prioritize ---
phase('Prioritize')
const goal = args ? (typeof args === 'string' ? args : args.goal) : null

let validIssues
if (goal) {
  log(`Goal: ${goal}`)
  const created = await agent(
    `You are the product manager. The user's goal is:

"${goal}"

Steps:
1. Run: gh issue list --state open --json number,title,labels --limit 20
2. Read relevant source files to understand current state.
3. Decide what issue(s) to create to achieve the goal.
4. For each issue, run: gh issue create --title "..." --body "..." --label "type:feature" --label "priority:high"
   Body must have ## Goal and ## Acceptance Criteria sections with checkboxes.
5. Return the created issues as an array.

Return an array of the issues you created (number, title, labels, and acceptance criteria as string array).`,
    { label: 'product-manager', agentType: 'product-manager', schema: { type: 'array', items: ISSUE_SCHEMA } }
  )
  validIssues = (created || []).filter(Boolean)
  log(`Created ${validIssues.length} issue(s)`)
} else {
  log('No goal provided — picking from open backlog')
  const picked = await agent(
    `You are the product manager. No new goal was given — pick the best issue(s) from the open backlog.

Steps:
1. Run: gh issue list --state open --json number,title,body,labels --limit 30
2. Score issues by: priority label > bugs over features > age > clarity of acceptance criteria.
3. Pick 1-3 highest-value issues that are ready to implement (have clear AC, aren't blocked).
4. If a chosen issue lacks acceptance criteria in its body, add them via: gh issue comment <N> --body "## Acceptance Criteria\n- [ ] ..."
5. Return the picked issues.

Return an array of issues (number, title, labels, and acceptance criteria as string array).`,
    { label: 'product-manager-pick', agentType: 'product-manager', schema: { type: 'array', items: ISSUE_SCHEMA } }
  )
  validIssues = (picked || []).filter(Boolean)
  log(`Picked ${validIssues.length} issue(s) from backlog`)
}

// --- Phase 2: Estimate ---
phase('Estimate')
const estimates = await parallel(validIssues.map(issue => () =>
  agent(
    `Estimate GitHub issue #${issue.number}: "${issue.title}"

Acceptance criteria:
${issue.ac.map(a => '- ' + a).join('\n')}

Steps:
1. Read relevant source files to assess complexity.
2. Post a comment on the issue: gh issue comment ${issue.number} --body "## Estimate: <S|M|L|XL>\n\n## Approach\n...\n\n## Risks\n..."
3. Add size label: gh issue edit ${issue.number} --add-label "size:<S|M|L|XL>"
4. If XL, set should_split=true and explain in approach what sub-issues you'd create.

Return your estimate.`,
    { label: `estimate-#${issue.number}`, agentType: 'tech-lead', schema: ESTIMATE_SCHEMA }
  )
))

const doable = validIssues.filter((issue, i) => estimates[i] && !estimates[i].should_split)
const tooLarge = validIssues.filter((issue, i) => estimates[i] && estimates[i].should_split)
if (tooLarge.length) log(`${tooLarge.length} issue(s) marked XL — skipping implementation, need splitting`)
log(`${doable.length} issue(s) ready for implementation`)

// --- Phase 3: Implement ---
phase('Implement')
const fixes = await pipeline(
  doable,
  issue => {
    const isUI = (issue.labels || []).some(l => /front|ui|css|html/i.test(l))
    const agentType = isUI ? 'frontend-expert' : 'springboot-expert'
    return agent(
      `Implement GitHub issue #${issue.number}: "${issue.title}"

Acceptance criteria:
${issue.ac.map(a => '- ' + a).join('\n')}

Branch naming: Use the pattern <type>/ISSUE-<number>-<slug>
- type: feature (new functionality), bugfix (bug), chore (cleanup/config)
- slug: 2-4 lowercase words from the title, hyphenated
- Example: feature/ISSUE-${issue.number}-add-health-check

Steps:
1. Create and checkout branch: git checkout -b <branch-name>
2. Read relevant files.
3. Implement the feature/fix.
4. Run mvn test -q (skip for frontend-only). If tests fail, fix them. If still failing, return fixed=false.
5. Commit: feat(#${issue.number}): <short description>
6. Push: git push -u origin HEAD
7. Open draft PR: gh pr create --title "feat(#${issue.number}): <desc>" --body "Closes #${issue.number}" --base main --draft

Only push/PR if tests pass. Return fixed, pr_url, and summary.`,
      { label: `dev-#${issue.number}`, agentType, isolation: 'worktree', schema: FIX_SCHEMA }
    )
  }
)

const implemented = fixes.filter(f => f && f.fixed && f.pr_url)
log(`${implemented.length}/${doable.length} implemented with PRs`)

if (!implemented.length) {
  log('No PRs to test or review — stopping.')
  return { issues: validIssues.length, implemented: 0, shipped: [] }
}

// --- Phase 4: Test ---
phase('Test')
const prNumbers = implemented.map(f => f.pr_url.match(/\/pull\/(\d+)/)?.[1]).filter(Boolean)

const qaResults = []
for (let i = 0; i < prNumbers.length; i++) {
  const prNum = prNumbers[i]
  const result = await agent(
    `QA issue #${doable[i].number}, PR #${prNum}.

Acceptance criteria:
${doable[i].ac.map(a => '- ' + a).join('\n')}

Steps:
1. Checkout the PR branch: gh pr checkout ${prNum}
2. Run mvn test -q — must pass.
3. gh pr diff ${prNum} — read what changed.
4. For UI changes: use Playwright to verify (browser_navigate to http://localhost:8080, browser_snapshot).
5. Check each acceptance criterion.
6. Verdict: gh pr review ${prNum} --approve or --request-changes with reason.
7. Checkout back to main: git checkout main

Return passed (boolean) and details.`,
    { label: `qa-#${prNum}`, agentType: 'qa-engineer', schema: QA_SCHEMA }
  )
  qaResults.push(result)
}

const passed = prNumbers.filter((_, i) => qaResults[i] && qaResults[i].passed)
log(`QA: ${passed.length}/${prNumbers.length} passed`)

// --- Phase 5: Review ---
phase('Review')
const reviews = await parallel(passed.map(prNum => () =>
  agent(
    `Review PR #${prNum}.

Steps:
1. gh pr diff ${prNum} — read full diff.
2. Check for correctness bugs, security issues, design violations.
3. Post inline comments for any findings.
4. Final verdict: gh pr review ${prNum} --approve or --request-changes.

Return approved (boolean), comments_posted count, and summary.`,
    { label: `review-#${prNum}`, agentType: 'code-reviewer', schema: REVIEW_SCHEMA }
  )
))

const approved = passed.filter((_, i) => reviews[i] && reviews[i].approved)

// --- Phase 6: Ship ---
phase('Ship')
const shipped = approved.map(prNum => `https://github.com/${prNum}`)
log(`${approved.length} PR(s) approved and ready for merge: ${approved.join(', ')}`)

return {
  issues: validIssues.length,
  implemented: implemented.length,
  qa_passed: passed.length,
  approved: approved.length,
  ready_to_merge: approved.map(n => implemented.find(f => f.pr_url.includes(n))?.pr_url).filter(Boolean),
}
