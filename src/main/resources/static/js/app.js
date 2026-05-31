// ----------------------------------------------------------------
// State
// ----------------------------------------------------------------
let currentStep      = 1;
let currentTopic     = '';
let currentQuestions = [];
let currentJobId     = null;
let activeEventSource = null;

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
    PENDING:     'bg-slate-600 text-slate-200',
    IN_PROGRESS: 'bg-yellow-600 text-yellow-100',
    COMPLETED:   'bg-green-700 text-green-100',
    FAILED:      'bg-red-700 text-red-100',
  };
  const cls = map[status] || 'bg-slate-600 text-slate-200';
  return `<span class="inline-block text-xs font-semibold px-2 py-0.5 rounded-full ${cls}">${status}</span>`;
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
    else if (isActive)  circleClass += 'bg-blue-500 text-white ring-2 ring-blue-300 ring-offset-2 ring-offset-slate-800';
    else                circleClass += 'bg-slate-700 text-slate-400';
    circle.className = circleClass;
    circle.innerHTML = isCompleted
      ? '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"/></svg>'
      : num;

    const label = document.createElement('span');
    label.className = 'ml-2 text-sm font-medium ' + (isActive ? 'text-slate-100' : 'text-slate-500');
    label.textContent = step.label;

    const item = document.createElement('div');
    item.className = 'flex items-center';
    item.appendChild(circle);
    item.appendChild(label);
    container.appendChild(item);

    if (i < STEPS.length - 1) {
      const line = document.createElement('div');
      line.className = 'flex-1 h-px mx-3 ' + (num < currentStep ? 'bg-blue-600' : 'bg-slate-700');
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
  try {
    const res = await fetch('/api/jobs');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const jobs = await res.json();
    if (jobs.length === 0) {
      list.innerHTML = '<p class="text-slate-500 text-sm px-2 py-4 text-center">No jobs yet.</p>';
      return;
    }
    list.innerHTML = jobs.map(job => {
      const truncated = job.topic && job.topic.length > 32
        ? job.topic.slice(0, 32) + '...'
        : (job.topic || 'Untitled');
      return `
        <div
          class="px-3 py-2.5 rounded-lg cursor-pointer hover:bg-slate-700 transition-colors group"
          data-job-id="${job.id}"
          data-job-status="${escapeAttr(job.status)}"
          data-job-topic="${escapeAttr(job.topic)}"
          title="${escapeAttr(job.topic)}"
        >
          <div class="flex items-start justify-between gap-2">
            <span class="text-sm text-slate-200 leading-snug flex-1 min-w-0 truncate">${escapeHtml(truncated)}</span>
          </div>
          <div class="flex items-center gap-2 mt-1">
            ${statusBadge(job.status)}
            <span class="text-xs text-slate-500">${relativeTime(job.createdAt)}</span>
          </div>
        </div>`;
    }).join('');
    list.querySelectorAll('[data-job-id]').forEach(el => {
      el.addEventListener('click', () => {
        loadJob(
          parseInt(el.dataset.jobId, 10),
          el.dataset.jobTopic,
          el.dataset.jobStatus
        );
      });
    });
  } catch (e) {
    list.innerHTML = `<p class="text-red-400 text-sm px-2 py-4 text-center">Failed to load jobs</p>`;
  }
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
      renderReport(job);
    } catch (e) {
      alert('Failed to load job report: ' + e.message);
    }
    return;
  }

  const logBox      = document.getElementById('log-box');
  const statusDot   = document.getElementById('log-status-dot');
  const statusText  = document.getElementById('log-status-text');
  const failedPanel = document.getElementById('step3-failed-panel');

  logBox.innerHTML = '';
  failedPanel.classList.add('hidden');
  document.getElementById('step3-topic-label').textContent = `Topic: ${topic}`;
  goToStep(3);

  if (status === 'IN_PROGRESS') {
    startLogStream(jobId);
  } else if (status === 'PENDING') {
    statusDot.className   = 'w-2.5 h-2.5 rounded-full bg-slate-400 animate-pulse';
    statusText.textContent = 'Waiting to start...';
    appendLogLine(logBox, 'Job is queued and waiting to start...');
  } else if (status === 'FAILED') {
    try {
      const res = await fetch(`/api/jobs/${jobId}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const job = await res.json();
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
  const reportEl = document.getElementById('report-content');
  reportEl.innerHTML = job.report
    ? marked.parse(job.report)
    : '<p class="text-slate-400 italic">No report content available.</p>';
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
      <label class="block text-sm font-medium text-slate-300 mb-1.5" for="q-${i}">
        ${escapeHtml(q)}
      </label>
      <input
        id="q-${i}"
        type="text"
        data-question="${escapeAttr(q)}"
        placeholder="Your answer..."
        class="w-full bg-slate-700 border border-slate-600 rounded-lg px-4 py-2.5 text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
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

  const logBox      = document.getElementById('log-box');
  const statusDot   = document.getElementById('log-status-dot');
  const statusText  = document.getElementById('log-status-text');
  const failedPanel = document.getElementById('step3-failed-panel');

  logBox.innerHTML = '';
  failedPanel.classList.add('hidden');
  statusDot.className   = 'w-2.5 h-2.5 rounded-full bg-yellow-400 animate-pulse';
  statusText.textContent = 'Pipeline running...';

  const es = new EventSource(`/api/jobs/${jobId}/stream`);
  activeEventSource = es;

  es.onmessage = (event) => {
    appendLogLine(logBox, event.data);
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

function appendLogLine(logBox, text) {
  const line = document.createElement('div');
  line.className = 'whitespace-pre-wrap break-all leading-5 text-slate-300';
  if (/error|fail|exception/i.test(text))    line.className += ' text-red-400';
  else if (/warn/i.test(text))               line.className += ' text-yellow-400';
  else if (/complet|success|done/i.test(text)) line.className += ' text-green-400';
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
    appendLogLine(document.getElementById('log-box'), '[ERROR] Failed to fetch final report: ' + e.message);
  }
}

document.getElementById('try-again-btn').addEventListener('click', async () => {
  if (!currentJobId) { resetWizard(); return; }
  try {
    const res = await fetch(`/api/jobs/${currentJobId}/restart`, { method: 'POST' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    document.getElementById('log-box').innerHTML = '';
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
  document.getElementById('log-box').innerHTML = '';
  document.getElementById('report-content').innerHTML = '';
  document.getElementById('step3-failed-panel').classList.add('hidden');
  goToStep(1);
}

// ----------------------------------------------------------------
// Sidebar controls
// ----------------------------------------------------------------
document.getElementById('refresh-sidebar-btn').addEventListener('click', loadSidebar);

// ----------------------------------------------------------------
// Init
// ----------------------------------------------------------------
renderStepIndicator();
loadSidebar();
