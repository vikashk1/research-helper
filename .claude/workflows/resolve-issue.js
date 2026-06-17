export const meta = {
  name: 'resolve-issue',
  description: 'Fetch a GitHub issue, branch, implement, test, commit, open PR, QA, and code review — deterministically.',
  phases: [
    { title: 'Understand', detail: 'Fetch issue details and comments from GitHub' },
    { title: 'Branch', detail: 'Check or create feature branch from main' },
    { title: 'Implement', detail: 'Implement → test loop until tests pass' },
    { title: 'Commit & PR', detail: 'Commit and open PR once tests are green' },
    { title: 'QA', detail: 'QA review; loop back to implement if it fails' },
    { title: 'Review', detail: 'Code review; loop back to implement if changes requested' },
  ],
}

// Safety caps to prevent infinite loops
const MAX_IMPL_ATTEMPTS  = 5  // inner:  implement → test
const MAX_QA_ROUNDS      = 3  // middle: qa review
const MAX_REVIEW_ROUNDS  = 3  // outer:  pr review

const ISSUE_SCHEMA = {
  type: 'object',
  properties: {
    number:     { type: 'number' },
    title:      { type: 'string' },
    body:       { type: 'string' },
    labels:     { type: 'array', items: { type: 'string' } },
    ac:         { type: 'array', items: { type: 'string' } },
    needs_java: { type: 'boolean' },
    needs_ui:   { type: 'boolean' },
    summary:    { type: 'string' },
    assessment: { type: 'string' }
  },
  required: ['number', 'title', 'body', 'ac', 'needs_java', 'needs_ui', 'summary'],
}

const BRANCH_SCHEMA = {
  type: 'object',
  properties: {
    branch: { type: 'string' },
    is_new: { type: 'boolean' },
  },
  required: ['branch', 'is_new'],
}

const IMPL_SCHEMA = {
  type: 'object',
  properties: {
    done:          { type: 'boolean' },
    summary:       { type: 'string' },
    files_changed: { type: 'array', items: { type: 'string' } },
  },
  required: ['done', 'summary'],
}

const TEST_SCHEMA = {
  type: 'object',
  properties: {
    passed:         { type: 'boolean' },
    failure_output: { type: 'string' },
  },
  required: ['passed'],
}

const PR_SCHEMA = {
  type: 'object',
  properties: {
    pr_url:    { type: 'string' },
    pr_number: { type: 'number' },
  },
  required: ['pr_url', 'pr_number'],
}

const QA_SCHEMA = {
  type: 'object',
  properties: {
    passed:   { type: 'boolean' },
    failures: { type: 'array', items: { type: 'string' } },
    details:  { type: 'string' },
  },
  required: ['passed', 'details'],
}

const REVIEW_SCHEMA = {
  type: 'object',
  properties: {
    approved:        { type: 'boolean' },
    change_requests: { type: 'array', items: { type: 'string' } },
    summary:         { type: 'string' },
  },
  required: ['approved', 'summary'],
}

// ─── Args ──────────────────────────────────────────────────────────────────
const issueNumber = args && (typeof args === 'number' ? args : parseInt(args, 10))
if (!issueNumber) throw new Error('Pass the issue number as args, e.g. args: 25')

// ─── Phase 1: Understand ───────────────────────────────────────────────────
phase('Understand')

const issue = await agent(
  `Fetch GitHub issue #${issueNumber}:
  gh issue view ${issueNumber} --json number,title,body,labels,comments

Read the body AND all comments. Extract:
- ac: acceptance criteria as a string array
- needs_java: true if any Java/Spring/backend changes are needed
- needs_ui: true if any HTML/CSS/JS/frontend changes are needed
- summary: concise description of what must be done
- assessment: if a comment was added for assessment, concisely summarize it.`,
  { label: 'understand', phase: 'Understand', schema: ISSUE_SCHEMA }
)

if (!issue) throw new Error(`Could not fetch issue #${issueNumber}`)
log(`#${issue.number}: ${issue.title}`)
log(`Java: ${issue.needs_java} | UI: ${issue.needs_ui}`)
log(`Assessment: ${issue.assessment}`)
// ─── Phase 2: Branch ───────────────────────────────────────────────────────
phase('Branch')

const branchResult = await agent(
  `Check for an existing branch for issue #${issueNumber}:
  git branch -a | grep -i "ISSUE-${issueNumber}"

If found: check it out, return its name with is_new=false.
If not found: create from main using format <type>/ISSUE-${issueNumber}-<slug>
  (type: feat|fix|chore  slug: 2-4 words from "${issue.title}")
  git checkout main && git pull && git checkout -b <branch>
Return branch name and is_new.`,
  { label: 'branch', phase: 'Branch', schema: BRANCH_SCHEMA }
)

if (!branchResult) throw new Error('Branch step failed')
log(`Branch: ${branchResult.branch}`)

// Determine which agent(s) implement
const implAgentType = issue.needs_ui && !issue.needs_java ? 'frontend-expert' : 'springboot-expert'
const acBlock = issue.ac.map(a => `- ${a}`).join('\n')

// Helper: run implementation via the correct agent(s)
const runImplement = async (reason, label) => {
  const basePrompt = `Issue #${issue.number}: "${issue.title}"
Branch: ${branchResult.branch} (already checked out — do NOT create a new branch or open a PR)

Summary: ${issue.summary}

Acceptance criteria:
${acBlock}

${reason ? `Context for this iteration:\n${reason}\n` : ''}Do not commit.`

  if (issue.needs_java && issue.needs_ui) {
    await agent(basePrompt + '\n\nFocus on Java backend changes only.',
      { label: `${label}-backend`, phase: 'Implement', agentType: 'springboot-expert', schema: IMPL_SCHEMA })
    await agent(basePrompt + '\n\nBackend is done. Focus on frontend (HTML/CSS/JS) changes only.',
      { label: `${label}-frontend`, phase: 'Implement', agentType: 'frontend-expert', schema: IMPL_SCHEMA })
  } else {
    await agent(basePrompt, { label, phase: 'Implement', agentType: implAgentType, schema: IMPL_SCHEMA })
  }
}

// Helper: run tests and return result
const runTests = async (label) => agent(
  `Run: mvn test -q
Return passed=true if all tests pass, or passed=false with failure_output (relevant error lines only).`,
  { label, phase: 'Implement', schema: TEST_SCHEMA }
)

// ─── State ─────────────────────────────────────────────────────────────────
let pr = null
let reviewFeedback = ''
let qaFeedback = ''
let reviewRound = 0
let prApproved = false

// ═══════════════════════════════════════════════════════════════════════════
// OUTER LOOP — repeat until PR is approved
// ═══════════════════════════════════════════════════════════════════════════
while (!prApproved && reviewRound < MAX_REVIEW_ROUNDS) {

  let qaRound = 0
  let qaPassed = false

  // ─────────────────────────────────────────────────────────────────────────
  // MIDDLE LOOP — repeat until QA passes
  // ─────────────────────────────────────────────────────────────────────────
  while (!qaPassed && qaRound < MAX_QA_ROUNDS) {

    let implAttempt = 0
    let testsPassed = false

    // ───────────────────────────────────────────────────────────────────────
    // INNER LOOP — implement then test until tests are green
    // ───────────────────────────────────────────────────────────────────────
    while (!testsPassed && implAttempt < MAX_IMPL_ATTEMPTS) {
      phase('Implement')

      const reason = reviewFeedback || qaFeedback
      await runImplement(reason, `impl-r${reviewRound}-q${qaRound}-a${implAttempt}`)

      // Clear feedback after acting on it
      reviewFeedback = ''
      qaFeedback = ''

      const testResult = await runTests(`test-r${reviewRound}-q${qaRound}-a${implAttempt}`)
      testsPassed = testResult && testResult.passed

      if (!testsPassed) {
        log(`Tests failed (attempt ${implAttempt + 1}/${MAX_IMPL_ATTEMPTS}): ${(testResult?.failure_output || '').slice(0, 120)}`)
        // Carry failure into next implement iteration as context
        qaFeedback = `Test failures to fix:\n${testResult?.failure_output || 'unknown'}`
      } else {
        log('Tests passed')
      }
      implAttempt++
    }

    if (!testsPassed) throw new Error(`Tests still failing after ${MAX_IMPL_ATTEMPTS} attempts — stopping.`)

    // ── Commit (push) after green tests ────────────────────────────────────
    phase('Commit & PR')

    if (!pr) {
      // First green commit — create the PR
      pr = await agent(
        `Commit all changes and open a pull request.

Commit format: <type>(ISSUE-${issueNumber}): <short description>
PR title: same as commit message
PR body must include "Closes #${issueNumber}"

Use the create-github-pr skill. Return pr_url and pr_number.`,
        { label: 'commit-pr', phase: 'Commit & PR', schema: PR_SCHEMA }
      )
      if (!pr || !pr.pr_url) throw new Error('PR creation failed')
      log(`PR opened: ${pr.pr_url}`)
    } else {
      // Subsequent commits — just push
      await agent(
        `Commit all changes with format: <type>(ISSUE-${issueNumber}): <short description>
Then push to branch ${branchResult.branch}.`,
        { label: `commit-push-r${reviewRound}-q${qaRound}`, phase: 'Commit & PR', schema: IMPL_SCHEMA }
      )
      log(`Changes pushed to ${branchResult.branch}`)
    }

    // ── QA review ──────────────────────────────────────────────────────────
    phase('QA')
    qaRound++
    log(`QA round ${qaRound}/${MAX_QA_ROUNDS}`)

    const qaResult = await agent(
      `Validate PR #${pr.pr_number} for issue #${issueNumber}.

Acceptance criteria:
${acBlock}`,
      { label: `qa-round-${qaRound}`, phase: 'QA', agentType: 'qa-engineer', schema: QA_SCHEMA }
    )

    qaPassed = qaResult && qaResult.passed

    if (!qaPassed) {
      qaFeedback = `QA failures (round ${qaRound}):\n` + (qaResult?.failures?.join('\n') || qaResult?.details || 'unknown')
      log(`QA failed: ${qaFeedback.slice(0, 200)}`)
    } else {
      log('QA passed')
    }
  }

  if (!qaPassed) {
    log(`QA did not pass after ${MAX_QA_ROUNDS} rounds — proceeding to review with known issues`)
  }

  // ── Code review ──────────────────────────────────────────────────────────
  phase('Review')
  reviewRound++
  log(`Review round ${reviewRound}/${MAX_REVIEW_ROUNDS}`)

  const reviewResult = await agent(
      `Review PR #${pr.pr_number}.`,
    { label: `review-round-${reviewRound}`, phase: 'Review', agentType: 'pr-reviewer', schema: REVIEW_SCHEMA }
  )

  prApproved = reviewResult && reviewResult.approved

  if (!prApproved) {
    reviewFeedback = `Review changes requested (round ${reviewRound}):\n` +
      (reviewResult?.change_requests?.join('\n') || reviewResult?.summary || 'unknown')
    log(`Changes requested: ${reviewFeedback.slice(0, 200)}`)
  } else {
    log('PR approved')
  }
}

// ─── Final report ──────────────────────────────────────────────────────────
return {
  issue:           issue.number,
  title:           issue.title,
  branch:          branchResult.branch,
  pr_url:          pr?.pr_url,
  pr_number:       pr?.pr_number,
  review_approved: prApproved,
}
