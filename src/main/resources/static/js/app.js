// ----------------------------------------------------------------
// Pricing table — keyed by server-supplied modelId.
// Add rows here as new models are deployed; all values are USD per million tokens.
// ----------------------------------------------------------------
const MODEL_PRICING = {
  'claude-haiku-4-5':          { inputPerM: 0.80,  outputPerM: 4.00  },
  'claude-haiku-3-5':          { inputPerM: 0.80,  outputPerM: 4.00  },
  'claude-sonnet-4-5':         { inputPerM: 3.00,  outputPerM: 15.00 },
  'claude-sonnet-4':           { inputPerM: 3.00,  outputPerM: 15.00 },
  'claude-sonnet-3-7':         { inputPerM: 3.00,  outputPerM: 15.00 },
  'claude-opus-4':             { inputPerM: 15.00, outputPerM: 75.00 },
  'claude-3-opus-20240229':    { inputPerM: 15.00, outputPerM: 75.00 },
};

/**
 * Compute estimated USD cost from token counts and model ID.
 * Returns null for costUsd when the model is not in the pricing table.
 * @param {number} inputTokens
 * @param {number} outputTokens
 * @param {string|null|undefined} modelId
 * @returns {{ totalTokens: number, costUsd: number|null }}
 */
function computeTokenCost(inputTokens, outputTokens, modelId) {
  const totalTokens = (inputTokens || 0) + (outputTokens || 0);
  const pricing = modelId ? MODEL_PRICING[modelId] : null;
  if (!pricing) {
    return { totalTokens, costUsd: null };
  }
  const costUsd =
    ((inputTokens  || 0) / 1_000_000) * pricing.inputPerM +
    ((outputTokens || 0) / 1_000_000) * pricing.outputPerM;
  return { totalTokens, costUsd };
}

/**
 * Format token count as e.g. "12.3k" or "1.2M".
 * @param {number} n
 * @returns {string}
 */
function formatTokenCount(n) {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
  if (n >= 1_000)     return (n / 1_000).toFixed(1) + 'k';
  return String(n);
}

// ----------------------------------------------------------------
// State
// ----------------------------------------------------------------
let currentStep      = 1;
let currentTopic     = '';
let currentQuestions = [];
let currentJobId     = null;
let activeEventSource = null;

// Tracks stage names (e.g. 'SEARCH') that have already been hydrated from the
// API response so that arriving SSE events for the same stage are silently dropped.
const seenStages = new Set();

// Tracks which accordion panels are currently open. Multiple panels may be open
// simultaneously — the active stage is auto-expanded by the stage event handlers,
// and completed stages are freely togglable by the user.
const accordionOpen = { SEARCH: false, SUMMARIZE: false, FORMAT: false };

const STEPS = [
  { label: 'Topic' },
  { label: 'Questions' },
  { label: 'Running' },
  { label: 'Report' },
];

// ----------------------------------------------------------------
// Utilities
// ----------------------------------------------------------------
function relativeTime(isoString) {
  const diff = Date.now() - new Date(isoString).getTime();
  const s = Math.floor(diff / 1000);
  if (s < 60)     return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60)     return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24)     return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

function statusBadge(status) {
  const map = {
    PENDING:     'bg-slate-300 text-slate-800 dark:bg-slate-600 dark:text-slate-200',
    IN_PROGRESS: 'bg-yellow-200 text-yellow-900 dark:bg-yellow-600 dark:text-yellow-100',
    COMPLETED:   'bg-green-200 text-green-900 dark:bg-green-700 dark:text-green-100',
    FAILED:      'bg-red-200 text-red-900 dark:bg-red-700 dark:text-red-100',
  };
  const cls = map[status] || 'bg-slate-300 text-slate-800 dark:bg-slate-600 dark:text-slate-200';
  return `<span class="inline-block text-xs font-semibold px-2 py-0.5 rounded-full ${cls}">${status}</span>`;
}

// ----------------------------------------------------------------
// Confirmation dialog
// ----------------------------------------------------------------
function showConfirmDialog(title, message) {
  return new Promise((resolve) => {
    const dialog  = document.getElementById('confirm-dialog');
    const titleEl = document.getElementById('confirm-dialog-title');
    const msgEl   = document.getElementById('confirm-dialog-message');
    const okBtn   = document.getElementById('confirm-dialog-ok');
    const cancelBtn = document.getElementById('confirm-dialog-cancel');

    titleEl.textContent = title;
    msgEl.textContent   = message;
    dialog.classList.remove('hidden');

    function cleanup(result) {
      dialog.classList.add('hidden');
      okBtn.removeEventListener('click', onOk);
      cancelBtn.removeEventListener('click', onCancel);
      dialog.removeEventListener('click', onBackdrop);
      resolve(result);
    }

    function onOk()      { cleanup(true);  }
    function onCancel()  { cleanup(false); }
    function onBackdrop(e) { if (e.target === dialog) cleanup(false); }

    okBtn.addEventListener('click', onOk);
    cancelBtn.addEventListener('click', onCancel);
    dialog.addEventListener('click', onBackdrop);
  });
}

function showError(elId, msg) {
  const el = document.getElementById(elId);
  el.textContent = msg;
  el.classList.remove('hidden');
}

function hideError(elId) {
  document.getElementById(elId).classList.add('hidden');
}

function setButtonLoading(btnId, spinnerId, textId, loading, defaultText) {
  const btn = document.getElementById(btnId);
  document.getElementById(spinnerId).classList.toggle('hidden', !loading);
  document.getElementById(textId).textContent = loading ? 'Please wait...' : defaultText;
  btn.disabled = loading;
  btn.classList.toggle('opacity-70', loading);
  btn.classList.toggle('cursor-not-allowed', loading);
}

// ----------------------------------------------------------------
// Step indicator
// ----------------------------------------------------------------
function renderStepIndicator() {
  const container = document.getElementById('step-indicators');
  container.innerHTML = '';
  STEPS.forEach((step, i) => {
    const num = i + 1;
    const isActive    = num === currentStep;
    const isCompleted = num < currentStep;

    const circle = document.createElement('div');
    let circleClass = 'w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold shrink-0 ';
    if (isCompleted)    circleClass += 'bg-blue-600 text-white';
    else if (isActive)  circleClass += 'bg-blue-500 text-white ring-2 ring-blue-300 ring-offset-2 ring-offset-slate-100 dark:ring-offset-slate-800';
    else                circleClass += 'bg-slate-300 text-slate-600 dark:bg-slate-700 dark:text-slate-400';
    circle.className = circleClass;
    circle.innerHTML = isCompleted
      ? '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"/></svg>'
      : num;

    const label = document.createElement('span');
    label.className = 'ml-2 text-sm font-medium ' + (isActive ? 'text-slate-900 dark:text-slate-100' : 'text-slate-400 dark:text-slate-500');
    label.textContent = step.label;

    const item = document.createElement('div');
    item.className = 'flex items-center';
    item.appendChild(circle);
    item.appendChild(label);
    container.appendChild(item);

    if (i < STEPS.length - 1) {
      const line = document.createElement('div');
      line.className = 'flex-1 h-px mx-3 ' + (num < currentStep ? 'bg-blue-600' : 'bg-slate-300 dark:bg-slate-700');
      container.appendChild(line);
    }
  });
}

// ----------------------------------------------------------------
// Navigation
// ----------------------------------------------------------------
function goToStep(n) {
  document.querySelectorAll('.step-panel').forEach(el => el.classList.add('hidden'));
  document.getElementById(`step-${n}`).classList.remove('hidden');
  currentStep = n;
  renderStepIndicator();
}

// ----------------------------------------------------------------
// Sidebar
// ----------------------------------------------------------------
async function loadSidebar() {
  const list = document.getElementById('job-list');
  const clearBtn = document.getElementById('clear-completed-btn');
  try {
    const res = await fetch('/api/jobs');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const jobs = await res.json();

    // Show/hide "Clear completed" button based on whether any completed jobs exist
    const hasCompleted = jobs.some(j => j.status === 'COMPLETED');
    clearBtn.classList.toggle('hidden', !hasCompleted);

    // Aggregate token usage across all jobs for the sidebar footer
    renderSidebarTokenFooter(jobs);

    if (jobs.length === 0) {
      list.innerHTML = '<p class="text-slate-400 dark:text-slate-500 text-sm px-2 py-4 text-center">No jobs yet.</p>';
      return;
    }
    list.innerHTML = jobs.map(job => {
      const truncated = job.topic && job.topic.length > 32
        ? job.topic.slice(0, 32) + '...'
        : (job.topic || 'Untitled');
      return `
        <div
          class="px-3 py-2.5 rounded-lg cursor-pointer hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors group relative"
          data-job-id="${job.id}"
          data-job-status="${escapeAttr(job.status)}"
          data-job-topic="${escapeAttr(job.topic)}"
          title="${escapeAttr(job.topic)}"
        >
          <div class="flex items-start justify-between gap-2">
            <span class="text-sm text-slate-800 dark:text-slate-200 leading-snug flex-1 min-w-0 truncate">${escapeHtml(truncated)}</span>
            <button
              class="delete-job-btn shrink-0 opacity-0 group-hover:opacity-100 focus:opacity-100 text-slate-400 hover:text-red-500 dark:text-slate-500 dark:hover:text-red-400 transition-opacity transition-colors p-0.5 rounded"
              data-delete-id="${job.id}"
              data-delete-topic="${escapeAttr(truncated)}"
              title="Delete this job"
              aria-label="Delete job"
            >
              <svg class="w-3.5 h-3.5 pointer-events-none" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
              </svg>
            </button>
          </div>
          <div class="flex items-center gap-2 mt-1">
            ${statusBadge(job.status)}
            <span class="text-xs text-slate-400 dark:text-slate-500">${relativeTime(job.createdAt)}</span>
          </div>
        </div>`;
    }).join('');

    // Load job on row click (but not on trash button)
    list.querySelectorAll('[data-job-id]').forEach(el => {
      el.addEventListener('click', (e) => {
        if (e.target.closest('.delete-job-btn')) return;
        loadJob(
          parseInt(el.dataset.jobId, 10),
          el.dataset.jobTopic,
          el.dataset.jobStatus
        );
      });
    });

    // Trash icon click handlers
    list.querySelectorAll('.delete-job-btn').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const jobId    = parseInt(btn.dataset.deleteId, 10);
        const jobTopic = btn.dataset.deleteTopic;
        await deleteJob(jobId, jobTopic);
      });
    });
  } catch (e) {
    list.innerHTML = `<p class="text-red-600 dark:text-red-400 text-sm px-2 py-4 text-center">Failed to load jobs</p>`;
    renderSidebarTokenFooter([]);
  }
}

/**
 * Render (or hide) the aggregate token-usage footer in the sidebar.
 * Costs are computed per-job using each job's own modelId so that mixed-model
 * pipelines are priced correctly. Jobs with an unknown modelId still contribute
 * to the token total; cost is omitted only when no job has a priced model.
 * @param {Array} jobs  — raw Job objects from GET /api/jobs (include modelId field)
 */
function renderSidebarTokenFooter(jobs) {
  const footer = document.getElementById('sidebar-token-footer');
  if (!footer) return;

  const completedJobs = jobs.filter(j => j.status === 'COMPLETED');
  if (completedJobs.length === 0) {
    footer.classList.add('hidden');
    return;
  }

  let totalTokens   = 0;
  let totalCostUsd  = 0;
  let hasPricedCost = false;

  completedJobs.forEach(j => {
    const { totalTokens: jobTokens, costUsd } = computeTokenCost(
      j.totalInputTokens  || 0,
      j.totalOutputTokens || 0,
      j.modelId
    );
    totalTokens += jobTokens;
    if (costUsd !== null) {
      totalCostUsd  += costUsd;
      hasPricedCost  = true;
    }
  });

  const countEl = footer.querySelector('.sidebar-token-count');
  const costEl  = footer.querySelector('.sidebar-token-cost');
  if (countEl) countEl.textContent = `~${formatTokenCount(totalTokens)} tokens`;
  if (costEl) {
    if (hasPricedCost) {
      costEl.textContent = `$${totalCostUsd.toFixed(4)} est.`;
      costEl.classList.remove('hidden');
    } else {
      costEl.textContent = '';
      costEl.classList.add('hidden');
    }
  }

  footer.classList.remove('hidden');
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function escapeAttr(str) {
  return String(str || '').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

async function loadJob(jobId, topic, status) {
  currentJobId = jobId;
  currentTopic = topic;

  if (status === 'COMPLETED') {
    try {
      const res = await fetch(`/api/jobs/${jobId}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const job = await res.json();
      // Populate the stage progress rail from persisted data before navigating
      // to the report view.  resetStageUI + hydrateStagesFromApi keeps the set
      // consistent even if a previous job's stages were loaded earlier.
      resetStageUI();
      hydrateStagesFromApi(job.stages);
      renderReport(job);
    } catch (e) {
      alert('Failed to load job report: ' + e.message);
    }
    return;
  }

  const statusDot   = document.getElementById('log-status-dot');
  const statusText  = document.getElementById('log-status-text');
  const failedPanel = document.getElementById('step3-failed-panel');

  failedPanel.classList.add('hidden');
  resetStageUI();
  document.getElementById('step3-topic-label').textContent = `Topic: ${topic}`;
  goToStep(3);

  if (status === 'IN_PROGRESS') {
    // Hydrate past stages from the API before opening the SSE stream so the user
    // sees already-completed stages immediately, without waiting for SSE events.
    try {
      const res = await fetch(`/api/jobs/${jobId}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const job = await res.json();
      hydrateStagesFromApi(job.stages);
    } catch (e) {
      // Non-fatal: SSE will still drive the UI forward from the current stage
    }
    startLogStream(jobId);
  } else if (status === 'PENDING') {
    statusDot.className   = 'w-2.5 h-2.5 rounded-full bg-slate-400 animate-pulse';
    statusText.textContent = 'Waiting to start...';
    // Open the first stage accordion and show a pending message
    setAccordionOpen('SEARCH', true);
    const searchLog = getStageLogBox('SEARCH');
    if (searchLog) appendLogLine(searchLog, 'Job is queued and waiting to start...');
    // No stages to hydrate for a PENDING job; the rail stays in its default state
  } else if (status === 'FAILED') {
    try {
      const res = await fetch(`/api/jobs/${jobId}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const job = await res.json();
      hydrateStagesFromApi(job.stages);
      statusDot.className   = 'w-2.5 h-2.5 rounded-full bg-red-400';
      statusText.textContent = 'Pipeline failed.';
      failedPanel.classList.remove('hidden');
      document.getElementById('step3-error-msg').textContent = job.errorMessage || 'Pipeline failed.';
    } catch (e) {
      alert('Failed to load job details: ' + e.message);
    }
  }
}

function renderReport(job) {
  document.getElementById('step4-topic-label').textContent = job.topic || '';

  // Render token usage / cost badge when token data is available
  const badgeEl = document.getElementById('step4-cost-badge');
  const inputTokens  = job.totalInputTokens  || 0;
  const outputTokens = job.totalOutputTokens || 0;
  if (badgeEl) {
    if (inputTokens > 0 || outputTokens > 0) {
      const { totalTokens, costUsd } = computeTokenCost(inputTokens, outputTokens, job.modelId);
      const tokensLabel = `~${formatTokenCount(totalTokens)} tokens`;
      const costLabel = costUsd !== null
        ? ` / $${costUsd.toFixed(4)}`
        : '';
      const modelLabel = job.modelId ? ` (${job.modelId})` : '';
      badgeEl.textContent = tokensLabel + costLabel + modelLabel;
      badgeEl.title = `Input: ${inputTokens.toLocaleString()} tokens, Output: ${outputTokens.toLocaleString()} tokens`;
      badgeEl.classList.remove('hidden');
    } else {
      badgeEl.classList.add('hidden');
    }
  }

  const reportEl = document.getElementById('report-content');
  reportEl.innerHTML = job.report
    ? marked.parse(job.report)
    : '<p class="text-slate-400 dark:text-slate-400 italic">No report content available.</p>';
  goToStep(4);
}

// ----------------------------------------------------------------
// Step 1 — Enter Topic
// ----------------------------------------------------------------
document.getElementById('clarify-btn').addEventListener('click', async () => {
  const topic = document.getElementById('topic-input').value.trim();
  hideError('step1-error');

  if (!topic) {
    showError('step1-error', 'Please enter a research topic.');
    return;
  }

  setButtonLoading('clarify-btn', 'clarify-spinner', 'clarify-btn-text', true, 'Start Research');

  try {
    const res = await fetch('/api/jobs/clarify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ topic }),
    });
    if (!res.ok) {
      const msg = await res.text().catch(() => `HTTP ${res.status}`);
      throw new Error(msg || `HTTP ${res.status}`);
    }
    const questions = await res.json();
    currentTopic     = topic;
    currentQuestions = questions;
    buildQuestionsForm(questions);
    goToStep(2);
  } catch (e) {
    showError('step1-error', 'Failed to fetch clarifying questions: ' + e.message);
  } finally {
    setButtonLoading('clarify-btn', 'clarify-spinner', 'clarify-btn-text', false, 'Start Research');
  }
});

document.getElementById('topic-input').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') document.getElementById('clarify-btn').click();
});

// ----------------------------------------------------------------
// Step 2 — Clarifying Questions
// ----------------------------------------------------------------
function buildQuestionsForm(questions) {
  const container = document.getElementById('questions-container');
  container.innerHTML = questions.map((q, i) => `
    <div>
      <label class="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5" for="q-${i}">
        ${escapeHtml(q)}
      </label>
      <input
        id="q-${i}"
        type="text"
        data-question="${escapeAttr(q)}"
        placeholder="Your answer..."
        class="w-full bg-white border border-slate-300 dark:bg-slate-700 dark:border-slate-600 rounded-lg px-4 py-2.5 text-slate-900 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
      >
    </div>
  `).join('');
}

document.getElementById('start-pipeline-btn').addEventListener('click', async () => {
  hideError('step2-error');

  const clarificationAnswers = {};
  document.querySelectorAll('#questions-container input').forEach(input => {
    clarificationAnswers[input.dataset.question] = input.value.trim();
  });

  setButtonLoading('start-pipeline-btn', 'pipeline-spinner', 'pipeline-btn-text', true, 'Start Pipeline');

  try {
    const res = await fetch('/api/jobs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ topic: currentTopic, clarificationAnswers }),
    });
    if (!res.ok) {
      const msg = await res.text().catch(() => `HTTP ${res.status}`);
      throw new Error(msg || `HTTP ${res.status}`);
    }
    const job = await res.json();
    currentJobId = job.id;
    loadSidebar();
    resetStageUI();
    startLogStream(job.id);
    goToStep(3);
    document.getElementById('step3-topic-label').textContent = `Topic: ${currentTopic}`;
  } catch (e) {
    showError('step2-error', 'Failed to start pipeline: ' + e.message);
  } finally {
    setButtonLoading('start-pipeline-btn', 'pipeline-spinner', 'pipeline-btn-text', false, 'Start Pipeline');
  }
});

document.getElementById('back-to-step1-btn').addEventListener('click', () => goToStep(1));

// ----------------------------------------------------------------
// Step 3 — Live Log Stream
// ----------------------------------------------------------------
function startLogStream(jobId) {
  if (activeEventSource) {
    activeEventSource.close();
    activeEventSource = null;
  }

  const statusDot   = document.getElementById('log-status-dot');
  const statusText  = document.getElementById('log-status-text');
  const failedPanel = document.getElementById('step3-failed-panel');

  // resetStageUI is intentionally NOT called here: callers that need a clean
  // rail (new pipelines from step 2, try-again restart) must call it first.
  // loadJob calls resetStageUI + hydrateStagesFromApi before startLogStream so
  // that already-completed stages are visible before any SSE event arrives.
  failedPanel.classList.add('hidden');
  statusDot.className   = 'w-2.5 h-2.5 rounded-full bg-yellow-400 animate-pulse';
  statusText.textContent = 'Pipeline running...';

  const es = new EventSource(`/api/jobs/${jobId}/stream`);
  activeEventSource = es;

  // Named 'stage' event — drives the pipeline stage indicator and accordion
  es.addEventListener('stage', (event) => {
    handleStageEvent(event.data);
  });

  // Plain (unnamed) SSE messages carry only job-level status signals
  // (COMPLETED / FAILED).  All stage activity lines arrive as named 'stage'
  // events handled above, so we must NOT route these messages to any stage
  // log box — doing so was an unreliable heuristic that could produce duplicate
  // or misrouted entries.
  es.onmessage = (event) => {
    if (event.data.includes('COMPLETED')) {
      closeStream();
      markStreamDone(true);
      fetchAndShowReport(jobId);
    } else if (event.data.includes('FAILED')) {
      closeStream();
      markStreamDone(false, event.data);
      loadSidebar();
    }
  };

  es.onerror = () => {
    if (es.readyState === EventSource.CLOSED) {
      closeStream();
      if (currentStep === 3) {
        fetch(`/api/jobs/${jobId}`)
          .then(r => r.json())
          .then(job => {
            if (job.status === 'COMPLETED') {
              markStreamDone(true);
              renderReport(job);
            } else if (job.status === 'FAILED') {
              markStreamDone(false, job.errorMessage || 'Pipeline failed.');
            } else {
              markStreamDone(true);
            }
            loadSidebar();
          })
          .catch(() => markStreamDone(false, 'Lost connection. Please check the job list.'));
      }
    }
  };

  function closeStream() {
    if (activeEventSource) {
      activeEventSource.close();
      activeEventSource = null;
    }
  }

  function markStreamDone(success, errMsg) {
    if (success) {
      statusDot.className   = 'w-2.5 h-2.5 rounded-full bg-green-400';
      statusText.textContent = 'Pipeline completed.';
    } else {
      statusDot.className   = 'w-2.5 h-2.5 rounded-full bg-red-400';
      statusText.textContent = 'Pipeline failed.';
      failedPanel.classList.remove('hidden');
      document.getElementById('step3-error-msg').textContent = errMsg || 'An error occurred.';
    }
  }
}

// ----------------------------------------------------------------
// Accordion helpers
// ----------------------------------------------------------------

/**
 * Returns the per-stage log container div, or null if the stage name is unknown.
 */
function getStageLogBox(stageName) {
  return document.getElementById(`log-${stageName}`);
}

/**
 * Open or close a stage accordion panel.
 * @param {string} stageName  e.g. 'SEARCH'
 * @param {boolean} open
 */
function setAccordionOpen(stageName, open) {
  const panel = document.getElementById(`accordion-${stageName}`);
  if (!panel) return;
  accordionOpen[stageName] = open;
  panel.classList.toggle('accordion-open', open);
  const chevron = panel.querySelector('.accordion-chevron');
  if (chevron) chevron.classList.toggle('accordion-chevron-open', open);
}

/**
 * Update the "active" badge visibility on a stage accordion header.
 * @param {string}  stageName
 * @param {boolean} active
 */
function setAccordionActiveBadge(stageName, active) {
  const target = document.getElementById(`accordion-${stageName}`);
  if (!target) return;
  const badge = target.querySelector('.accordion-stage-badge');
  if (!badge) return;
  if (active) {
    badge.textContent = 'active';
    badge.classList.remove('hidden');
  } else {
    badge.classList.add('hidden');
  }
}

/**
 * Wire up accordion toggle click handlers (called once on init).
 */
function initAccordionHandlers() {
  document.querySelectorAll('.accordion-header').forEach(btn => {
    btn.addEventListener('click', () => {
      const stageName = btn.dataset.stage;
      const isOpen = accordionOpen[stageName];
      setAccordionOpen(stageName, !isOpen);
    });
  });
}

// ----------------------------------------------------------------
// Pipeline stage indicator
// ----------------------------------------------------------------
const STAGE_ORDER = ['SEARCH', 'SUMMARIZE', 'FORMAT'];

function resetStageUI() {
  // Reset accordion open state eagerly so stale toggle state cannot persist
  // across job switches, even if the DOM elements are not yet available.
  STAGE_ORDER.forEach(s => { accordionOpen[s] = false; });

  STAGE_ORDER.forEach(stageName => {
    const el = document.getElementById(`stage-${stageName}`);
    if (!el) return;
    el.classList.remove('stage-active', 'stage-done');

    const icon    = el.querySelector('.stage-icon');
    const num     = el.querySelector('.stage-num');
    const spinner = el.querySelector('.stage-spinner');
    const check   = el.querySelector('.stage-check');
    const elapsed = el.querySelector('.stage-elapsed');

    // Remove only state-driven classes; CSS base styles on .stage-icon handle the rest
    icon.classList.remove('stage-icon-active', 'stage-icon-done');
    num.classList.remove('hidden');
    spinner.classList.add('hidden');
    check.classList.add('hidden');
    elapsed.textContent = '';
    elapsed.classList.add('hidden');

    // Clear the per-stage accordion log and close the panel
    const stageLog = getStageLogBox(stageName);
    if (stageLog) stageLog.innerHTML = '';
    setAccordionOpen(stageName, false);
    setAccordionActiveBadge(stageName, false);
  });

  // Reset connector lines
  document.querySelectorAll('#pipeline-stages .stage-connector').forEach(el => {
    el.className = 'stage-connector flex-1 h-px mx-3 bg-slate-300 dark:bg-slate-700';
  });

  // Clear deduplication set so hydration works cleanly on the next job load
  seenStages.clear();
}

// ----------------------------------------------------------------
// Stage hydration from persisted API data
// ----------------------------------------------------------------
// stages: array of JobStageDto from GET /api/jobs/{id}
// Each entry: { stage: 'SEARCH'|'SUMMARIZE'|'FORMAT', status: 'PENDING'|'ACTIVE'|'COMPLETED'|'FAILED', startedAt, endedAt }
function hydrateStagesFromApi(stages) {
  if (!Array.isArray(stages) || stages.length === 0) return;

  // Deactivate any currently-active stage first (belt-and-braces reset)
  STAGE_ORDER.forEach(s => {
    const el = document.getElementById(`stage-${s}`);
    if (el) el.classList.remove('stage-active');
  });

  stages.forEach(stageDto => {
    const stageName = String(stageDto.stage).toUpperCase();
    const el = document.getElementById(`stage-${stageName}`);
    if (!el) return;

    const status = String(stageDto.status).toUpperCase();

    if (status === 'COMPLETED' || status === 'FAILED') {
      el.classList.remove('stage-active');
      el.classList.add('stage-done');

      // Compute elapsed time client-side from startedAt / endedAt
      if (stageDto.startedAt && stageDto.endedAt) {
        const start   = new Date(stageDto.startedAt).getTime();
        const end     = new Date(stageDto.endedAt).getTime();
        const seconds = ((end - start) / 1000).toFixed(1);
        const elapsedEl = el.querySelector('.stage-elapsed');
        if (elapsedEl) {
          elapsedEl.textContent = `${seconds}s`;
          elapsedEl.classList.remove('hidden');
        }
        const labelEl = el.querySelector('.stage-label');
        if (labelEl) {
          labelEl.title = `Completed in ${seconds}s`;
        }
      }

      // Populate per-stage log from persisted log lines if provided
      if (Array.isArray(stageDto.logLines)) {
        const stageLog = getStageLogBox(stageName);
        if (stageLog) {
          stageDto.logLines.forEach(line => appendLogLine(stageLog, line));
        }
      }

      // Open the accordion so the user can see the completed stage's logs
      setAccordionOpen(stageName, true);
      setAccordionActiveBadge(stageName, false);

      // Mark as seen so duplicate SSE 'end' events are dropped
      seenStages.add(stageName);

    } else if (status === 'ACTIVE') {
      el.classList.remove('stage-done');
      el.classList.add('stage-active');

      // Auto-expand the active stage and show the badge
      setAccordionOpen(stageName, true);
      setAccordionActiveBadge(stageName, true);

      // Do NOT add to seenStages — SSE 'end' event should still fire for this stage
    }
    // PENDING: leave in default (unstyled) state — nothing to do
  });
}

function handleStageEvent(data) {
  let event;
  try {
    event = JSON.parse(data);
  } catch (e) {
    return;
  }

  const { stage, type, message, elapsed } = event;
  const stageName = String(stage).toUpperCase();
  const el = document.getElementById(`stage-${stageName}`);
  if (!el) return;

  const stageLog = getStageLogBox(stageName);

  if (type === 'start') {
    // Deactivate any previously active stage — hide its badge but keep it open (togglable)
    STAGE_ORDER.forEach(s => {
      const prev = document.getElementById(`stage-${s}`);
      if (prev && prev.classList.contains('stage-active')) {
        prev.classList.remove('stage-active');
        setAccordionActiveBadge(s, false);
      }
    });
    el.classList.remove('stage-done');
    el.classList.add('stage-active');

    // Auto-expand this stage's accordion
    setAccordionOpen(stageName, true);
    setAccordionActiveBadge(stageName, true);

    if (stageLog && message) {
      appendLogLine(stageLog, message);
    }

  } else if (type === 'end') {
    // If this stage was already hydrated from the API response, drop the SSE event
    // to avoid duplicate state transitions or elapsed-time flicker.
    if (seenStages.has(stageName)) {
      return;
    }
    el.classList.remove('stage-active');
    el.classList.add('stage-done');

    // Remove the "active" badge — the panel stays open so the user can review logs
    setAccordionActiveBadge(stageName, false);

    // Show elapsed time (convert ms to seconds with one decimal)
    const seconds = elapsed != null ? (elapsed / 1000).toFixed(1) : null;
    const elapsedEl = el.querySelector('.stage-elapsed');
    if (seconds !== null) {
      elapsedEl.textContent = `${seconds}s`;
      elapsedEl.classList.remove('hidden');
    }

    // Also update the label to include elapsed for screen-reader / tooltip clarity
    const labelEl = el.querySelector('.stage-label');
    if (seconds !== null) {
      labelEl.title = `Completed in ${seconds}s`;
    }

    // Record as seen so any late-arriving duplicate SSE events are ignored
    seenStages.add(stageName);

    if (stageLog && message) {
      appendLogLine(stageLog, message);
    }

  } else if (type === 'activity') {
    // Append activity message to the correct stage's log panel
    if (stageLog && message) {
      appendLogLine(stageLog, message);
    }
  }
}

function appendLogLine(logBox, text) {
  const line = document.createElement('div');
  line.className = 'whitespace-pre-wrap break-all leading-5 text-slate-600 dark:text-slate-300';
  if (/error|fail|exception/i.test(text))      line.className = 'whitespace-pre-wrap break-all leading-5 text-red-600 dark:text-red-400';
  else if (/warn/i.test(text))                  line.className = 'whitespace-pre-wrap break-all leading-5 text-yellow-600 dark:text-yellow-400';
  else if (/complet|success|done/i.test(text))  line.className = 'whitespace-pre-wrap break-all leading-5 text-green-700 dark:text-green-400';
  line.textContent = text;
  logBox.appendChild(line);
  logBox.scrollTop = logBox.scrollHeight;
}

async function fetchAndShowReport(jobId) {
  try {
    const res = await fetch(`/api/jobs/${jobId}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const job = await res.json();
    loadSidebar();
    renderReport(job);
  } catch (e) {
    // Append the error to whichever stage log is currently open, or FORMAT as fallback
    const fallbackLog = getStageLogBox('FORMAT') || getStageLogBox('SEARCH');
    if (fallbackLog) appendLogLine(fallbackLog, '[ERROR] Failed to fetch final report: ' + e.message);
  }
}

document.getElementById('try-again-btn').addEventListener('click', async () => {
  if (!currentJobId) { resetWizard(); return; }
  try {
    const res = await fetch(`/api/jobs/${currentJobId}/restart`, { method: 'POST' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    resetStageUI();
    document.getElementById('step3-failed-panel').classList.add('hidden');
    loadSidebar();
    startLogStream(currentJobId);
  } catch (e) {
    alert('Failed to restart job: ' + e.message);
  }
});

// ----------------------------------------------------------------
// Step 4 — New Research
// ----------------------------------------------------------------
document.getElementById('new-research-btn').addEventListener('click', resetWizard);

// ----------------------------------------------------------------
// Reset
// ----------------------------------------------------------------
function resetWizard() {
  if (activeEventSource) {
    activeEventSource.close();
    activeEventSource = null;
  }
  currentTopic     = '';
  currentQuestions = [];
  currentJobId     = null;
  document.getElementById('topic-input').value = '';
  document.getElementById('questions-container').innerHTML = '';
  document.getElementById('report-content').innerHTML = '';
  document.getElementById('step3-failed-panel').classList.add('hidden');
  resetStageUI();
  goToStep(1);
}

// ----------------------------------------------------------------
// Delete job (single)
// ----------------------------------------------------------------
async function deleteJob(jobId, jobTopic) {
  const confirmed = await showConfirmDialog(
    'Delete Job',
    `Are you sure you want to delete the job "${jobTopic}"?`
  );
  if (!confirmed) return;

  try {
    const res = await fetch(`/api/jobs/${jobId}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    // If the deleted job is the one currently being viewed, reset to Step 1
    if (currentJobId === jobId) {
      resetWizard();
    }
    loadSidebar();
  } catch (e) {
    alert('Failed to delete job: ' + e.message);
  }
}

// ----------------------------------------------------------------
// Clear all completed jobs (bulk)
// ----------------------------------------------------------------
async function clearCompletedJobs() {
  const confirmed = await showConfirmDialog(
    'Clear Completed Jobs',
    'Are you sure you want to delete all completed jobs? This cannot be undone.'
  );
  if (!confirmed) return;

  try {
    const res = await fetch('/api/jobs/completed', { method: 'DELETE' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();

    // If the currently viewed job was completed and thus deleted, reset to Step 1
    if (currentJobId !== null) {
      const checkRes = await fetch(`/api/jobs/${currentJobId}`).catch(() => null);
      if (!checkRes || checkRes.status === 404) {
        resetWizard();
      }
    }
    loadSidebar();
  } catch (e) {
    alert('Failed to clear completed jobs: ' + e.message);
  }
}

// ----------------------------------------------------------------
// Sidebar controls
// ----------------------------------------------------------------
document.getElementById('refresh-sidebar-btn').addEventListener('click', loadSidebar);
document.getElementById('clear-completed-btn').addEventListener('click', clearCompletedJobs);

// ----------------------------------------------------------------
// Init
// ----------------------------------------------------------------
renderStepIndicator();
loadSidebar();
initAccordionHandlers();
