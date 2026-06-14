export const meta = {
  name: 'sdlc',
  description: 'Full SDLC: prioritize → estimate → create issues → develop → test → review → merge',
  phases: [
    { title: 'Prioritize', detail: 'PM decides what to build next' },
    { title: 'Estimate', detail: 'Tech lead assesses feasibility' },
    { title: 'Plan', detail: 'Story writer creates GitHub issues' },
    { title: 'Develop', detail: 'Developers implement in worktrees' },
    { title: 'Test', detail: 'QA validates against acceptance criteria' },
    { title: 'Review', detail: 'Code reviewer checks PR quality' },
  ],
}

const PRIORITY_SCHEMA = {
  type: 'array',
  items: {
    type: 'object',
    properties: {
      title: { type: 'string' },
      why: { type: 'string' },
      acceptance_criteria: { type: 'array', items: { type: 'string' } },
      priority: { type: 'string', enum: ['P0', 'P1', 'P2'] },
    },
    required: ['title', 'why', 'acceptance_criteria', 'priority'],
  },
}

const ESTIMATE_SCHEMA = {
  type: 'object',
  properties: {
    feasible: { type: 'boolean' },
    size: { type: 'string', enum: ['S', 'M', 'L', 'XL'] },
    hours_estimate: { type: 'array', items: { type: 'number' } },
    approach: { type: 'string' },
    risks: { type: 'array', items: { type: 'string' } },
    touches: { type: 'array', items: { type: 'string' } },
    needs_agents: { type: 'array', items: { type: 'string' } },
  },
  required: ['feasible', 'size', 'approach', 'needs_agents'],
}

const ISSUE_SCHEMA = {
  type: 'object',
  properties: {
    number: { type: 'number' },
    title: { type: 'string' },
    url: { type: 'string' },
  },
  required: ['number', 'title'],
}

const FIX_SCHEMA = {
  type: 'object',
  properties: {
    fixed: { type: 'boolean' },
    summary: { type: 'string' },
    pr_number: { type: 'number' },
    pr_url: { type: 'string' },
  },
  required: ['fixed', 'summary'],
}

const QA_SCHEMA = {
  type: 'object',
  properties: {
    pass: { type: 'boolean' },
    bugs_found: { type: 'array', items: { type: 'object', properties: { description: { type: 'string' }, severity: { type: 'string' } }, required: ['description', 'severity'] } },
    recommendation: { type: 'string', enum: ['approve', 'fix-needed'] },
  },
  required: ['pass', 'recommendation'],
}

const REVIEW_SCHEMA = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['approve', 'request-changes'] },
    findings_count: { type: 'number' },
    summary: { type: 'string' },
  },
  required: ['verdict', 'summary'],
}

// --- args: { limit: number } (how many items to process, default 3) ---
const limit = (args && args.limit) || 3

// Phase 1: PM prioritizes
phase('Prioritize')
const items = await agent(
  `You are the product-manager. Analyze the repo and open issues, then return the top ${limit} priorities.
Run: gh issue list --state open --json number,title,labels,createdAt
Read DESIGN.md for product context.
Return a JSON array of prioritized items.`,
  { label: 'product-manager', agentType: 'product-manager', schema: PRIORITY_SCHEMA }
)
log(`PM prioritized ${items.length} items`)

// Phase 2: Tech lead estimates each
phase('Estimate')
const estimated = await pipeline(
  items,
  item => agent(
    `Assess this work item:
Title: ${item.title}
Why: ${item.why}
Acceptance Criteria: ${item.acceptance_criteria.join('; ')}

Read relevant source files. Return feasibility, size, approach, risks, files touched, and which agents are needed.`,
    { label: `estimate:${item.title.slice(0, 30)}`, agentType: 'tech-lead', schema: ESTIMATE_SCHEMA }
  )
)
const feasible = items.filter((_, i) => estimated[i] && estimated[i].feasible)
const estimates = estimated.filter(Boolean).filter(e => e.feasible)
log(`${feasible.length}/${items.length} items feasible`)

// Phase 3: Story writer creates issues
phase('Plan')
const issues = await pipeline(
  feasible,
  (item, _, i) => agent(
    `Create a GitHub issue for:
Title: ${item.title}
Priority: ${item.priority}
Acceptance Criteria:
${item.acceptance_criteria.map(c => '- ' + c).join('\n')}
Approach: ${estimates[i].approach}
Size: ${estimates[i].size}
Risks: ${(estimates[i].risks || []).join(', ') || 'none'}
Agents needed: ${estimates[i].needs_agents.join(', ')}

Run gh issue create with appropriate labels. Return the issue number, title, and URL.`,
    { label: `issue:${item.title.slice(0, 30)}`, agentType: 'story-writer', schema: ISSUE_SCHEMA }
  )
)
const created = issues.filter(Boolean)
log(`Created ${created.length} issues`)

// Phase 4: Develop
phase('Develop')
const fixes = await pipeline(
  created,
  (issue, _, i) => {
    const agentType = estimates[i].needs_agents.includes('frontend-expert') ? 'frontend-expert' : 'springboot-expert'
    return agent(
      `Fix GitHub issue #${issue.number}: ${issue.title}
Read the issue body: gh issue view ${issue.number} --json body -q .body

Steps:
1. Read relevant files.
2. Implement the fix/feature.
3. Run mvn test -q (skip for frontend-only). If tests fail, fix or return fixed=false.
4. Commit: fix(#${issue.number}): <short description>
5. Push: git push -u origin HEAD
6. Open draft PR: gh pr create --title "fix(#${issue.number}): ..." --body "Closes #${issue.number}" --base main --draft

Only push/PR if tests pass. Return fixed, summary, pr_number, pr_url.`,
      { label: `dev:#${issue.number}`, agentType, isolation: 'worktree', schema: FIX_SCHEMA }
    )
  }
)
const developed = fixes.filter(f => f && f.fixed && f.pr_number)
log(`${developed.length}/${created.length} implemented with PRs`)

// Phase 5: QA
phase('Test')
const tested = await pipeline(
  developed,
  fix => agent(
    `Validate PR #${fix.pr_number}.
1. Check out the PR branch: gh pr checkout ${fix.pr_number}
2. Run mvn test -q
3. Read the linked issue's acceptance criteria
4. For UI changes, use Playwright to verify
5. Report pass/fail per criterion and any bugs found.`,
    { label: `qa:PR#${fix.pr_number}`, agentType: 'qa-engineer', schema: QA_SCHEMA }
  )
)

// Phase 6: Review passing PRs
phase('Review')
const passing = developed.filter((_, i) => tested[i] && tested[i].pass)
const reviews = await pipeline(
  passing,
  fix => agent(
    `Review PR #${fix.pr_number}. Check diff for bugs, security, architecture violations.
Post inline comments for findings. Approve or request changes.`,
    { label: `review:PR#${fix.pr_number}`, agentType: 'code-reviewer', schema: REVIEW_SCHEMA }
  )
)

const approved = reviews.filter(r => r && r.verdict === 'approve')
log(`Result: ${created.length} issues → ${developed.length} PRs → ${passing.length} passed QA → ${approved.length} approved`)

return {
  prioritized: items.length,
  estimated: feasible.length,
  issues_created: created.length,
  prs_opened: developed.length,
  qa_passed: passing.length,
  approved: approved.length,
  approved_prs: passing.filter((_, i) => reviews[i] && reviews[i].verdict === 'approve').map(f => f.pr_url),
}
