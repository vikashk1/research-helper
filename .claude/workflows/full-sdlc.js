export const meta = {
  name: 'full-sdlc',
  description: 'End-to-end SDLC: prioritize, estimate, implement, test, review, ship',
  phases: [
    { title: 'Prioritize', detail: 'PM creates GitHub issues with acceptance criteria' },
    { title: 'Estimate', detail: 'Tech lead sizes issues — flows into Implement without barrier' },
    { title: 'Implement', detail: 'Dev agents fix issues in isolated worktrees' },
    { title: 'Test', detail: 'QA validates sequentially (one checkout at a time)' },
    { title: 'Review', detail: 'Code reviewer gates merge readiness' },
    { title: 'Ship', detail: 'Final PR URLs ready for human merge' },
  ],
}

const ISSUES_SCHEMA = {
  type: 'object',
  properties: {
    issues: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          number: { type: 'number' },
          title: { type: 'string' },
          labels: { type: 'array', items: { type: 'string' } },
          ac: { type: 'array', items: { type: 'string' } },
        },
        required: ['number', 'title', 'ac'],
      },
    },
  },
  required: ['issues'],
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
    `The user's goal is: "${goal}"

Create issue(s) to achieve it. Return them as {issues: [...]} with number, title, labels, and ac (acceptance criteria as string array).`,
    { label: 'product-manager', agentType: 'product-manager', schema: ISSUES_SCHEMA }
  )
  validIssues = (created && created.issues || []).filter(Boolean)
  log(`Created ${validIssues.length} issue(s)`)
} else {
  log('No goal provided — picking from open backlog')
  const picked = await agent(
    `No new goal given — pick from the open backlog. Return as {issues: [...]} with number, title, labels, and ac (acceptance criteria as string array).`,
    { label: 'product-manager-pick', agentType: 'product-manager', schema: ISSUES_SCHEMA }
  )
  validIssues = (picked && picked.issues || []).filter(Boolean)
  log(`Picked ${validIssues.length} issue(s) from backlog`)
}

// --- Phases 2+3: Estimate → Implement (per-issue pipeline, no barrier) ---
phase('Estimate')
const fixes = await pipeline(
  validIssues,
  issue => agent(
    `Estimate issue #${issue.number}: "${issue.title}"

Acceptance criteria:
${issue.ac.map(a => '- ' + a).join('\n')}

Post your estimate comment and add size label. Return your estimate.`,
    { label: `estimate-#${issue.number}`, phase: 'Estimate', agentType: 'tech-lead', schema: ESTIMATE_SCHEMA }
  ),
  (est, issue) => {
    if (!est || est.should_split) {
      log(`#${issue.number} is XL — skipping, needs splitting`)
      return null
    }
    const isUI = (issue.labels || []).some(l => /front|ui|css|html/i.test(l))
    const agentType = isUI ? 'frontend-expert' : 'springboot-expert'
    return agent(
      `Implement issue #${issue.number}: "${issue.title}"

Acceptance criteria:
${issue.ac.map(a => '- ' + a).join('\n')}

Branch: <type>/ISSUE-${issue.number}-<slug> (type: feature|bugfix|chore, slug: 2-4 words from title).
Only push and open a draft PR (with "Closes #${issue.number}") if all tests pass. Otherwise return fixed=false.`,
      { label: `dev-#${issue.number}`, phase: 'Implement', agentType, isolation: 'worktree', schema: FIX_SCHEMA }
    )
  }
)

const implemented = fixes.filter(f => f && f.fixed && f.pr_url)
log(`${implemented.length}/${validIssues.length} implemented with PRs`)

if (!implemented.length) {
  log('No PRs to test or review — stopping.')
  return { issues: validIssues.length, implemented: 0, shipped: [] }
}

// --- Phase 4: Test (serial — one branch checkout at a time) ---
phase('Test')
const qaResults = []
for (let i = 0; i < implemented.length; i++) {
  const prNum = implemented[i].pr_url.match(/\/pull\/(\d+)/)?.[1]
  if (!prNum) continue
  const issue = validIssues.find(iss => implemented[i].pr_url.includes(`ISSUE-${iss.number}`) || implemented[i].summary.includes(`#${iss.number}`))
  const result = await agent(
    `QA PR #${prNum}.${issue ? `\n\nAcceptance criteria:\n${issue.ac.map(a => '- ' + a).join('\n')}` : ''}

Validate and post your verdict on the PR.`,
    { label: `qa-#${prNum}`, agentType: 'qa-engineer', schema: QA_SCHEMA }
  )
  qaResults.push({ prNum, result })
}

const passed = qaResults.filter(q => q.result && q.result.passed).map(q => q.prNum)
log(`QA: ${passed.length}/${implemented.length} passed`)

// --- Phase 5: Review (parallel — no shared state) ---
phase('Review')
const reviews = await parallel(passed.map(prNum => () =>
  agent(
    `Review PR #${prNum}. Post inline comments and your final verdict.`,
    { label: `review-#${prNum}`, agentType: 'code-reviewer', schema: REVIEW_SCHEMA }
  )
))

const approved = passed.filter((_, i) => reviews[i] && reviews[i].approved)

// --- Phase 6: Ship ---
phase('Ship')
const readyToMerge = approved.map(n => implemented.find(f => f.pr_url.includes(`/pull/${n}`))?.pr_url).filter(Boolean)
log(`${approved.length} PR(s) approved and ready for merge: ${readyToMerge.join(', ')}`)

return {
  issues: validIssues.length,
  implemented: implemented.length,
  qa_passed: passed.length,
  approved: approved.length,
  ready_to_merge: readyToMerge,
}
