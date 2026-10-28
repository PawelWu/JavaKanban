/* ============================================
   Kanban Board - Frontend (Backend API)
   Communicates with Java Spring Boot backend
   ============================================ */

// =====================
// Configuration
// =====================
const API_BASE = ''; // Same origin as the server

// =====================
// DOM References
// =====================
const boardEl = document.getElementById('board');
const taskModal = document.getElementById('task-modal');
const columnModal = document.getElementById('column-modal');
const confirmModal = document.getElementById('confirm-modal');
const timeModal = document.getElementById('time-modal');
const logModal = document.getElementById('log-modal');
const subtaskModal = document.getElementById('subtask-modal');
const subtaskModalBody = document.getElementById('subtask-modal-body');
const subtaskForm = document.getElementById('subtask-form');
const taskForm = document.getElementById('task-form');
const columnForm = document.getElementById('column-form');
const timeForm = document.getElementById('time-form');

// =====================
// API helpers
// =====================
async function api(method, url, body = null) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  if (body) opts.body = JSON.stringify(body);

  const res = await fetch(url, opts);
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(err.message || `API ${method} ${url} failed (${res.status})`);
  }
  // DELETE returns no content
  if (res.status === 204) return null;
  return res.json();
}

async function loadBoard() {
  const columns = await api('GET', `${API_BASE}/api/columns`);
  state.columns = columns;
  render();
}

// =====================
// State
// =====================
let state = {
  columns: [],
};

// =====================
// Helpers
// =====================
function formatDate(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr + 'T00:00:00');
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function formatDuration(seconds) {
  if (seconds == null) return '';
  const s = Math.max(0, Math.round(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (h > 0) return `${h}h ${String(m).padStart(2, '0')}m`;
  if (m > 0) return `${m}m ${String(sec).padStart(2, '0')}s`;
  return `${sec}s`;
}

function formatDateTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function prettyUrl(url) {
  if (!url) return '';
  let s = url.replace(/^https?:\/\//, '');
  if (s.length > 32) s = s.slice(0, 32) + '\u2026';
  return s;
}

function getDeadlineStatus(deadline) {
  if (!deadline) return 'none';
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const dl = new Date(deadline + 'T00:00:00');
  if (dl < today) return 'overdue';
  if (+dl === +today) return 'today';
  return 'ok';
}

// =====================
// Rendering
// =====================
function render() {
  boardEl.innerHTML = '';

  state.columns.forEach(col => {
    const colEl = createColumnEl(col);
    boardEl.appendChild(colEl);
  });

  // Add "new column" button at end
  const addColBtn = document.createElement('div');
  addColBtn.className = 'add-column-card';
  addColBtn.textContent = '+ Add Column';
  addColBtn.addEventListener('click', openColumnModal);
  boardEl.appendChild(addColBtn);

  setupDropZones();
}

function createColumnEl(col) {
  const colWrapper = document.createElement('div');
  colWrapper.className = 'column';
  colWrapper.dataset.colId = col.id;

  // Header
  const header = document.createElement('div');
  header.className = 'column-header';

  const titleEl = document.createElement('span');
  titleEl.className = 'column-title';
  titleEl.textContent = col.title;
  titleEl.addEventListener('click', () => renameColumn(col.id));

  const countEl = document.createElement('span');
  countEl.className = 'task-count';
  countEl.textContent = col.taskCount || 0;

  const actions = document.createElement('div');
  actions.className = 'column-actions';

  const addTaskBtn = document.createElement('button');
  addTaskBtn.className = 'btn-ghost';
  addTaskBtn.textContent = '+';
  addTaskBtn.title = 'Add task';
  addTaskBtn.addEventListener('click', () => openTaskModal(col.id));

  const deleteColBtn = document.createElement('button');
  deleteColBtn.className = 'btn-ghost';
  deleteColBtn.innerHTML = '&times;';
  deleteColBtn.title = 'Delete column';
  deleteColBtn.addEventListener('click', () => deleteColumn(col.id));

  actions.append(addTaskBtn, deleteColBtn);
  header.append(titleEl, countEl, actions);
  colWrapper.appendChild(header);

  // Tasks container
  const dropZone = document.createElement('div');
  dropZone.className = 'column-drop-zone';
  dropZone.dataset.colId = col.id;

  if (!col.tasks || col.tasks.length === 0) {
    const emptyMsg = document.createElement('p');
    emptyMsg.className = 'empty-column-msg';
    emptyMsg.textContent = 'No tasks yet. Click + to add one.';
    dropZone.appendChild(emptyMsg);
  } else {
    col.tasks.forEach(task => {
      const card = createTaskCardEl(task, col.id);
      dropZone.appendChild(card);
    });
  }

  colWrapper.appendChild(dropZone);
  return colWrapper;
}

function createTaskCardEl(task, columnId) {
  const card = document.createElement('div');
  card.className = 'task-card';
  card.draggable = true;
  card.dataset.taskId = task.id;
  card.dataset.colId = columnId;

  // Priority bar
  const priorityBar = document.createElement('div');
  priorityBar.className = `task-priority-bar ${task.priority || 'medium'}`;
  card.appendChild(priorityBar);

  // Title
  const titleEl = document.createElement('div');
  titleEl.className = 'task-title-text';
  titleEl.textContent = task.title;
  card.appendChild(titleEl);

  // Description (if any)
  if (task.description) {
    const descEl = document.createElement('div');
    descEl.className = 'task-desc-text';
    descEl.textContent = task.description;
    card.appendChild(descEl);
  }

  // Subtask checklist (rows come with the task in the board payload).
  // Clicking a row toggles its checkbox; the card is draggable, so stop the event
  // from bubbling to the card's own handlers (dblclick-to-edit, dragstart).
  const subs = task.subtasks || [];
  if (subs.length > 0) {
    const list = document.createElement('div');
    list.className = 'subtask-list';
    subs.forEach((sub) => {
      const row = document.createElement('div');
      row.className = `subtask-row ${sub.done ? 'done' : 'open'}`;
      row.title = 'Click to toggle';

      const check = document.createElement('span');
      check.className = 'subtask-check';
      check.textContent = sub.done ? '\u2611' : '\u2610';
      row.appendChild(check);

      const text = document.createElement('span');
      text.className = 'subtask-text';
      text.textContent = sub.title;
      row.appendChild(text);

      row.addEventListener('click', (e) => {
        e.stopPropagation();
        e.preventDefault();
        toggleSubtask(columnId, task.id, sub.id).catch((err) => console.error('Toggle failed:', err));
      });

      list.appendChild(row);
    });
    card.appendChild(list);
  }

  // URL link (clickable from the main page)
  if (task.url) {
    const link = document.createElement('a');
    link.className = 'task-link';
    link.href = task.url;
    link.target = '_blank';
    link.rel = 'noreferrer';
    link.textContent = '\u{1F517} ' + prettyUrl(task.url);
    link.addEventListener('click', (e) => e.stopPropagation());
    card.appendChild(link);
  }

  // Meta row
  const meta = document.createElement('div');
  meta.className = 'task-meta';

  // Priority badge
  const priorityBadge = document.createElement('span');
  priorityBadge.className = `priority-badge ${task.priority || 'medium'}`;
  priorityBadge.textContent = task.priority || 'medium';
  meta.appendChild(priorityBadge);

  // Deadline badge
  const deadlineBadge = document.createElement('span');
  const dlStatus = getDeadlineStatus(task.deadline);
  if (dlStatus !== 'none') {
    deadlineBadge.className = `deadline-badge ${dlStatus}`;
    const label = dlStatus === 'overdue' ? 'Overdue: ' : dlStatus === 'today' ? 'Today: ' : '';
    deadlineBadge.textContent = label + formatDate(task.deadline);
  } else {
    deadlineBadge.className = 'deadline-badge none';
    deadlineBadge.textContent = 'No deadline';
  }
  meta.appendChild(deadlineBadge);

  // Time badge (total time spent on this task)
  const timeBadge = document.createElement('span');
  if (task.timeLogged > 0) {
    timeBadge.className = 'time-badge spent';
    timeBadge.textContent = '\u23F1 ' + formatDuration(task.timeLogged);
  } else if (task.running) {
    timeBadge.className = 'time-badge running';
    timeBadge.textContent = '\u25CF running';
  } else {
    timeBadge.className = 'time-badge none';
    timeBadge.textContent = '\u2014';
  }
  meta.appendChild(timeBadge);

  // Subtask progress (only rendered when rows exist - an empty badge is noise)
  if (subs.length > 0) {
    const doneCount = subs.filter((s) => s.done).length;
    const subBadge = document.createElement('span');
    subBadge.className = `subtask-badge ${doneCount === subs.length ? 'done' : 'progress'}`;
    subBadge.textContent = `\u2611 ${doneCount}/${subs.length}`;
    meta.appendChild(subBadge);
  }

  card.appendChild(meta);

  // Context menu button
  const menuBtn = document.createElement('button');
  menuBtn.className = 'task-menu-btn';
  menuBtn.innerHTML = '&#x2022;&#x2022;&#x2022;';
  menuBtn.title = 'Options';
  menuBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    showTaskMenu(e, task.id, columnId);
  });

  // Start / stop time-tracking buttons (only one is meaningful at a time)
  const timeActions = document.createElement('div');
  timeActions.className = 'time-actions';

  if (!task.running) {
    const startBtn = document.createElement('button');
    startBtn.className = 'time-btn start-btn';
    startBtn.textContent = '\u25B6';
    startBtn.title = 'Start time log';
    startBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      openTimeModal(columnId, task.id, 'start');
    });
    timeActions.appendChild(startBtn);
  } else {
    const stopBtn = document.createElement('button');
    stopBtn.className = 'time-btn stop-btn';
    stopBtn.textContent = '\u25A0';
    stopBtn.title = 'Stop time log';
    stopBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      openTimeModal(columnId, task.id, 'stop');
    });
    timeActions.appendChild(stopBtn);
  }

  // Manage subtasks from the card too (adds a row / toggles / deletes existing ones)
  const subActions = document.createElement('div');
  subActions.className = 'subtask-actions';
  const subBtn = document.createElement('button');
  subBtn.className = 'subtask-btn';
  subBtn.textContent = '\u2611';
  subBtn.title = 'Subtasks';
  subBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    openSubtaskModal(columnId, task.id);
  });
  subActions.appendChild(subBtn);

  const metaHolder = document.createElement('div');
  metaHolder.className = 'meta-holder';
  metaHolder.style.position = 'relative';
  metaHolder.appendChild(meta);
  metaHolder.appendChild(menuBtn);
  metaHolder.appendChild(timeActions);
  metaHolder.appendChild(subActions);
  card.appendChild(metaHolder);

  // Double-click to edit
  card.addEventListener('dblclick', () => openTaskModal(columnId, task.id));

  // Drag events for the card
  card.addEventListener('dragstart', handleDragStart);
  card.addEventListener('dragend', handleDragEnd);

  return card;
}

// =====================
// Task Context Menu
// =====================
function showTaskMenu(event, taskId, columnId) {
  closeTaskMenus();
  const target = event.target.closest('.task-card');
  if (!target) return;

  const menu = document.createElement('div');
  menu.className = 'task-context-menu';
  menu.innerHTML = `
    <button data-action="edit">Edit</button>
    <button data-action="subtasks">\u2611 Subtasks</button>
    <button data-action="info">\u23F1 Time log</button>
    <button data-action="delete" class="danger">Delete</button>
  `;

  // The menu lives in <body>, not in the card: .board is a scroll container
  // (overflow-x: auto) and a card with a transform/box-shadow creates a containing
  // block, so a menu inside the card gets clipped or positioned against the wrong box.
  document.body.appendChild(menu);

  const rect = event.target.getBoundingClientRect();
  const MENU_WIDTH = 180;
  const MENU_HEIGHT = 150;
  let top = rect.bottom + 4;
  if (top + MENU_HEIGHT > window.innerHeight) top = Math.max(0, window.innerHeight - MENU_HEIGHT);
  let right = window.innerWidth - rect.right;
  if (right + MENU_WIDTH > window.innerWidth) right = Math.max(0, window.innerWidth - MENU_WIDTH);
  menu.style.top = top + 'px';
  menu.style.right = right + 'px';

  menu.querySelectorAll('button').forEach(btn => {
    btn.addEventListener('click', () => {
      closeTaskMenus();
      if (btn.dataset.action === 'edit') openTaskModal(columnId, taskId);
      else if (btn.dataset.action === 'subtasks') openSubtaskModal(columnId, taskId);
      else if (btn.dataset.action === 'info') openLogModal(columnId, taskId);
      else deleteTask(taskId, columnId);
    });
  });

  setTimeout(() => document.addEventListener('click', closeTaskMenus, { once: true }), 0);
}

function closeTaskMenus() {
  document.querySelectorAll('.task-context-menu').forEach(m => m.remove());
}

// =====================
// Drag & Drop
// =====================
let dragData = null;

function handleDragStart(e) {
  const card = e.target.closest('.task-card');
  if (!card) return;

  dragData = { taskId: card.dataset.taskId, sourceColId: card.dataset.colId };
  card.classList.add('dragging');
  e.dataTransfer.effectAllowed = 'move';

  const img = new Image();
  img.src = 'data:image/gif;base64,R0lGODlhAQABAIAAAAUEBAAAACwAAAAAAQABAAACAkQBADs=';
  e.dataTransfer.setDragImage(img, 0, 0);
}

function handleDragEnd(e) {
  const card = e.target.closest('.task-card');
  if (card) card.classList.remove('dragging');

  document.querySelectorAll('.task-card.drop-preview').forEach(el => el.remove());
  document.querySelectorAll('.column.drag-over').forEach(el => el.classList.remove('drag-over'));
  dragData = null;
}

function setupDropZones() {
  document.querySelectorAll('.column-drop-zone').forEach(zone => {
    zone.addEventListener('dragover', handleDragOver);
    zone.addEventListener('dragleave', handleDragLeave);
    zone.addEventListener('drop', handleDrop);
  });
}

function getTaskPreviewElement(taskId) {
  const existing = document.querySelector(`.task-card.drop-preview[data-task-id="${taskId}"]`);
  if (existing) return existing;

  const preview = document.createElement('div');
  preview.className = 'task-card drop-preview';
  preview.dataset.taskId = taskId;

  const domCard = document.querySelector(`.task-card:not(.dragging)[data-task-id="${taskId}"]`);
  if (domCard) {
    const titleEl = domCard.querySelector('.task-title-text');
    preview.textContent = titleEl ? titleEl.textContent : 'Task';
  } else {
    preview.textContent = 'Task';
  }

  return preview;
}

function handleDragOver(e) {
  e.preventDefault();
  e.dataTransfer.dropEffect = 'move';

  const dropZone = e.currentTarget;
  const targetCard = e.target.closest('.task-card');

  // Clean up previews in other zones to prevent duplicates.
  document.querySelectorAll('.task-card.drop-preview').forEach(preview => {
    if (preview.parentElement !== dropZone) preview.remove();
  });

  let preview = dropZone.querySelector(`.task-card.drop-preview[data-task-id="${dragData.taskId}"]`);
  let needsNewPreview = !preview;

  if (!targetCard || !sourceCardExists(dragData.taskId)) {
    // Dropped on empty zone or no source card visible yet
    if (needsNewPreview) preview = getTaskPreviewElement(dragData.taskId);

    const cards = Array.from(dropZone.querySelectorAll('.task-card:not(.dragging):not(.drop-preview)'));
    let insertAfter = null;
    for (const card of cards) {
      if (e.clientY < card.getBoundingClientRect().top + card.getBoundingClientRect().height / 2) break;
      insertAfter = card;
    }

    // Only do DOM mutation if the preview needs repositioning.
    // Skip on empty columns when preview is already at the end —
    // this prevents a DOM write/flash every dragover event (60+ times/sec).
    if (needsNewPreview) {
      const emptyMsg = dropZone.querySelector('.empty-column-msg');
      if (insertAfter) dropZone.insertBefore(preview, insertAfter);
      else if (emptyMsg) dropZone.insertBefore(preview, emptyMsg);
      else dropZone.appendChild(preview);
    }
  } else {
    // Dropping on a card — use position to determine order.
    // Skip if preview already exists and is in the right spot
    // (avoids unnecessary DOM writes that cause flicker).
    const rect = targetCard.getBoundingClientRect();
    const shouldGoBefore = e.clientY < rect.top + rect.height / 2;

    if (needsNewPreview) {
      preview = getTaskPreviewElement(dragData.taskId);
      if (shouldGoBefore) {
        dropZone.insertBefore(preview, targetCard);
      } else {
        const next = targetCard.nextElementSibling;
        if (next) dropZone.insertBefore(preview, next);
        else dropZone.appendChild(preview);
      }
    } else {
      // Preview already exists — move it without recreating
      if (shouldGoBefore && targetCard.previousElementSibling !== preview) {
        dropZone.insertBefore(preview, targetCard);
      } else if (!shouldGoBefore && targetCard.nextElementSibling !== preview) {
        const next = targetCard.nextElementSibling;
        if (next) dropZone.insertBefore(preview, next);
        else if (dropZone.lastElementChild !== preview) {
          dropZone.appendChild(preview);
        }
      }
    }
  }

  dropZone.closest('.column').classList.add('drag-over');
}

function insertBefore(parent, taskId, refCard) {
  const preview = getTaskPreviewElement(taskId);
  parent.insertBefore(preview, refCard);
}

function insertAfter(parent, taskId, refCard) {
  const preview = getTaskPreviewElement(taskId);
  if (refCard.nextElementSibling) parent.insertBefore(preview, refCard.nextElementSibling);
  else parent.appendChild(preview);
}

function sourceCardExists(taskId) {
  return !!document.querySelector(`.task-card:not(.dragging)[data-task-id="${taskId}"]`);
}

function handleDragLeave(e) {
  const zone = e.currentTarget;
  const rect = zone.getBoundingClientRect();

  // Only do anything if the cursor has truly left this element's bounds.
  // Moving between child elements inside the same drop-zone should NOT fire cleanup.
  if (e.clientX >= rect.left && e.clientX <= rect.right &&
      e.clientY >= rect.top && e.clientY <= rect.bottom) {
    return;
  }

  zone.closest('.column').classList.remove('drag-over');
  zone.querySelectorAll('.task-card.drop-preview').forEach(el => el.remove());
}

async function handleDrop(e) {
  e.preventDefault();
  const dropZone = e.currentTarget;
  const targetColId = dropZone.dataset.colId;
  if (!dragData) return;

  // Remove visual preview
  document.querySelectorAll('.task-card.drop-preview').forEach(el => el.remove());

  try {
    if (targetColId !== dragData.sourceColId) {
      // Different column — move the task
      await api('PATCH', `${API_BASE}/api/columns/${dragData.sourceColId}/move`, {
        taskId: dragData.taskId,
        targetColumnId: targetColId,
      });
    } else {
      // Same column — reorder
      const preview = dropZone.querySelector(`.task-card.drop-preview[data-task-id="${dragData.taskId}"]`);
      let insertIndex = 0;

      if (preview) {
        const cards = Array.from(dropZone.querySelectorAll('.task-card:not(.drop-preview):not(.dragging)'));
        const idx = cards.indexOf(preview);
        // The new task is at 'idx' position, but all tasks were renumbered
        // Actually we need to compute the display order based on position
        insertIndex = idx;
        preview.remove();
      } else {
        // Dropped after last card -> append
        const cards = Array.from(dropZone.querySelectorAll('.task-card:not(.dragging)'));
        insertIndex = cards.length;
      }

      await api('PATCH', `${API_BASE}/api/columns/${targetColId}/tasks/${dragData.taskId}/reorder`, {
        displayOrder: insertIndex,
      });
    }

    await loadBoard();
  } catch (err) {
    console.error('Drop failed:', err);
    await loadBoard(); // Refresh to restore state
  }
}

// =====================
// Task CRUD (API calls)
// =====================
async function createTask(columnId, title, description, priority, deadline, url = null) {
  const task = await api('POST', `${API_BASE}/api/columns/${columnId}/tasks`, {
    title,
    description: description || '',
    priority: priority.toUpperCase(),
    deadline: deadline || null,
    url: url || null,
  });
  return task;
}

async function updateTask(columnId, taskId, updates) {
  const task = await api('PUT', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}`, {
    title: updates.title,
    description: updates.description || '',
    priority: updates.priority?.toUpperCase(),
    deadline: updates.deadline || null,
    url: updates.url || null,
  });
  return task;
}

async function startTime(columnId, taskId, description) {
  return api('POST', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}/time/start`, { description: description || null });
}

async function stopTime(columnId, taskId, description) {
  return api('POST', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}/time/stop`, { description: description || null });
}

async function deleteTask(taskId, columnId) {
  showConfirm('Delete Task', 'Are you sure you want to delete this task?', async () => {
    try {
      await api('DELETE', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}`);
      await loadBoard();
    } catch (err) {
      alert('Failed to delete task: ' + err.message);
    }
  });
}

// =====================
// Time tracking modals
// =====================
async function openTimeModal(columnId, taskId, mode) {
  const titleEl = document.getElementById('time-modal-title');
  const submitBtn = document.getElementById('time-submit-btn');
  const modeInput = document.getElementById('time-mode');
  const taskInput = document.getElementById('time-task-id');
  const colInput = document.getElementById('time-column-id');
  const descInput = document.getElementById('time-desc');
  const origDescInput = document.getElementById('time-orig-desc');

  const col = state.columns.find(c => c.id === columnId);
  const taskTitle = col?.tasks?.find(t => t.id === taskId)?.title || 'Task';

  // On STOP, show the comment that was typed at START (fetch the running log fresh so a
  // stale board snapshot cannot lose it).
  let runningLog = null;
  if (mode === 'stop') {
    try {
      const fresh = await api('GET', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}`);
      runningLog = (fresh.timeLogs || []).find(l => l.running);
    } catch (err) {
      // Fall back to whatever the board snapshot knows.
      const col = state.columns.find(c => c.id === columnId);
      const snapshot = col?.tasks?.find(t => t.id === taskId);
      runningLog = (snapshot?.timeLogs || []).find(l => l.running);
    }
  }

  if (mode === 'stop') {
    const started = runningLog ? `  \u00b7 started ${formatDateTime(runningLog.startedAt)}` : '';
    titleEl.textContent = `STOP \u2014 ${taskTitle}${started}`;
    submitBtn.textContent = '\u25A0 Stop';
  } else {
    titleEl.textContent = `START \u2014 ${taskTitle}`;
    submitBtn.textContent = '\u25B6 Start';
  }

  const original = runningLog?.description || '';
  origDescInput.value = original;
  descInput.value = original;

  modeInput.value = mode;
  taskInput.value = taskId;
  colInput.value = columnId;

  timeModal.classList.remove('hidden');
  setTimeout(() => descInput.focus(), 100);
}

async function timeModalSubmit() {
  const mode = document.getElementById('time-mode').value;
  const columnId = document.getElementById('time-column-id').value;
  const taskId = document.getElementById('time-task-id').value;
  const original = document.getElementById('time-orig-desc').value;
  const typed = document.getElementById('time-desc').value.trim();

  // Unchanged text (the STOP prompt prefills the START comment) -> send nothing new.
  const description = typed === original.trim() ? null : typed;

  try {
    if (mode === 'stop') await stopTime(columnId, taskId, description);
    else await startTime(columnId, taskId, description);
    closeTimeModal();
    await loadBoard();
  } catch (err) {
    alert('Time tracking failed: ' + err.message);
    closeTimeModal();
    await loadBoard();
  }
}

function closeTimeModal() {
  timeModal.classList.add('hidden');
  timeForm.reset();
}

timeForm.addEventListener('submit', (e) => {
  e.preventDefault();
  timeModalSubmit();
});

// =====================
// Subtask CRUD (one level below a task)
// =====================
async function createSubtask(columnId, taskId, title) {
  return api('POST', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}/subtasks`, { title });
}

async function listSubtasks(columnId, taskId) {
  return api('GET', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}/subtasks`);
}

async function renameSubtask(columnId, taskId, subtaskId, title) {
  return api('PUT', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}/subtasks/${subtaskId}`, { title });
}

// done omitted -> the server flips the current state. Values are sent as strings because
// the endpoint deserializes Map<String,String>.
async function toggleSubtask(columnId, taskId, subtaskId, done = null) {
  const body = done == null ? {} : { done: String(done) };
  return api('PATCH', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}/subtasks/${subtaskId}/toggle`, body);
}

async function deleteSubtask(columnId, taskId, subtaskId) {
  return api('DELETE', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}/subtasks/${subtaskId}`);
}

// =====================
// Subtask modal (add a row / toggle / delete existing rows)
// =====================
async function openSubtaskModal(columnId, taskId) {
  let rows;
  try {
    rows = await listSubtasks(columnId, taskId);
  } catch (err) {
    alert('Failed to load subtasks: ' + err.message);
    return;
  }

  const col = state.columns.find(c => c.id === columnId);
  const taskTitle = col?.tasks?.find(t => t.id === taskId)?.title || 'Task';
  document.getElementById('subtask-modal-title').textContent = `Subtasks \u2014 ${taskTitle}`;
  document.getElementById('sub-column-id').value = columnId;
  document.getElementById('sub-task-id').value = taskId;
  document.getElementById('subtask-title').value = '';

  const html = rows.map((s) =>
    `<div class="subtask-edit-row" data-id="${esc(s.id)}">` +
    `<button class="subtask-toggle-btn" data-id="${esc(s.id)}">${s.done ? '\u2611' : '\u2610'}</button>` +
    `<button class="subtask-delete-btn" data-id="${esc(s.id)}">Delete</button>` +
    `<span class="subtask-edit-text">${esc(s.title)}</span>` +
    '</div>');
  if (rows.length === 0) {
    html.push('<p class="subtask-empty">No subtasks yet \u2014 type a title above and press Add.</p>');
  }
  subtaskModalBody.innerHTML = html.join('');

  subtaskModal.classList.remove('hidden');
  setTimeout(() => document.getElementById('subtask-title').focus(), 100);
}

function closeSubtaskModal() {
  subtaskModal.classList.add('hidden');
  subtaskModalBody.innerHTML = '';
  subtaskForm.reset();
}

subtaskForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const columnId = document.getElementById('sub-column-id').value;
  const taskId = document.getElementById('sub-task-id').value;
  const title = document.getElementById('subtask-title').value.trim();
  if (!title) return;

  try {
    await createSubtask(columnId, taskId, title);
    closeSubtaskModal();
    await loadBoard();
  } catch (err) {
    alert('Failed to add subtask: ' + err.message);
    closeSubtaskModal();
    await loadBoard();
  }
});

// Rows are built with innerHTML, so the toggle/delete buttons are handled by delegation
// on the body container (rebuilt every time the modal opens).
subtaskModalBody.addEventListener('click', async (e) => {
  const btn = e.target;
  if (!btn.classList.contains('subtask-toggle-btn') && !btn.classList.contains('subtask-delete-btn')) return;

  const subtaskId = btn.dataset.id;
  if (!subtaskId) return;
  const columnId = document.getElementById('sub-column-id').value;
  const taskId = document.getElementById('sub-task-id').value;

  try {
    if (btn.classList.contains('subtask-toggle-btn')) await toggleSubtask(columnId, taskId, subtaskId);
    else await deleteSubtask(columnId, taskId, subtaskId);
    closeSubtaskModal();
    await loadBoard();
  } catch (err) {
    alert('Subtask update failed: ' + err.message);
    closeSubtaskModal();
    await loadBoard();
  }
});

// =====================
// Task info page (time log)
// =====================
function esc(value) {
  if (value == null) return '';
  return String(value).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

async function openLogModal(columnId, taskId) {
  let task;
  try {
    task = await api('GET', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}`);
  } catch (err) {
    alert('Failed to load task: ' + err.message);
    return;
  }

  document.getElementById('log-modal-title').textContent = `Task Info \u2014 ${task.title}`;

  const fields = [
    ['Priority', (task.priority || 'medium').toUpperCase()],
    ['Deadline', task.deadline || 'none'],
    ['URL', task.url || ''],
    ['Created', formatDateTime(task.createdAt)],
    ['Updated', formatDateTime(task.updatedAt)],
    ['Time spent', formatDuration(task.timeLogged)],
    ['Status', task.running ? 'RUNNING' : 'idle'],
  ];

  const html = [];
  html.push('<div class="info-grid">');
  fields.forEach(([label, value]) => {
    let valueHtml = esc(value);
    if (label === 'URL' && task.url) {
      valueHtml = `<a href="${esc(task.url)}" target="_blank" rel="noreferrer">${esc(prettyUrl(task.url))}</a>`;
    }
    html.push(`<div class="info-item"><span class="info-label">${esc(label)}</span><span class="info-value">${valueHtml}</span></div>`);
  });
  html.push('</div>');

  const logs = task.timeLogs || [];
  if (logs.length === 0) {
    html.push('<p class="log-empty">No time logs yet. Press \u25B6 on the card to start one.</p>');
  } else {
    logs.forEach((log, i) => {
      const stopLabel = log.running ? 'RUNNING' : formatDateTime(log.stoppedAt);
      html.push(
        `<div class="log-entry ${log.running ? 'running' : 'closed'}">` +
        `<span class="log-index">#${logs.length - i}</span>` +
        `<span class="log-start">${formatDateTime(log.startedAt)}</span>` +
        `<span class="log-stop">${stopLabel}</span>` +
        `<span class="log-duration">${formatDuration(log.durationSeconds)}</span>` +
        `<span class="log-desc">${esc(log.description)}</span>` +
        '</div>'
      );
    });
  }

  document.getElementById('log-modal-body').innerHTML = html.join('');
  logModal.classList.remove('hidden');
}

function closeLogModal() {
  logModal.classList.add('hidden');
}

async function openTaskModal(columnId, taskId = null) {
  const modalTitle = document.getElementById('modal-title');
  const submitBtn = document.getElementById('task-submit-btn');
  const colIdInput = document.getElementById('task-column-id');
  const idInput = document.getElementById('task-id');
  const titleInput = document.getElementById('task-title');
  const descInput = document.getElementById('task-desc');
  const priorityInput = document.getElementById('task-priority');
  const deadlineInput = document.getElementById('task-deadline');
  const urlInput = document.getElementById('task-url');

  if (taskId) {
    try {
      const task = await api('GET', `${API_BASE}/api/columns/${columnId}/tasks/${taskId}`);
      modalTitle.textContent = 'Edit Task';
      submitBtn.textContent = 'Save Changes';
      idInput.value = task.id;
      colIdInput.value = columnId;
      titleInput.value = task.title;
      descInput.value = task.description || '';
      priorityInput.value = task.priority || 'medium';
      deadlineInput.value = task.deadline || '';
      urlInput.value = task.url || '';
    } catch (err) {
      alert('Failed to load task: ' + err.message);
      return;
    }
  } else {
    modalTitle.textContent = 'New Task';
    submitBtn.textContent = 'Create Task';
    idInput.value = '';
    colIdInput.value = columnId;
    titleInput.value = '';
    descInput.value = '';
    priorityInput.value = 'medium';
    deadlineInput.value = '';
    urlInput.value = '';
  }

  taskModal.classList.remove('hidden');
  setTimeout(() => titleInput.focus(), 100);
}

function closeTaskModal() {
  taskModal.classList.add('hidden');
  taskForm.reset();
}

taskForm.addEventListener('submit', async (e) => {
  e.preventDefault();

  const taskId = document.getElementById('task-id').value;
  const columnId = document.getElementById('task-column-id').value;
  const title = document.getElementById('task-title').value.trim();
  const description = document.getElementById('task-desc').value.trim();
  const priority = document.getElementById('task-priority').value;
  const deadline = document.getElementById('task-deadline').value;

  if (!title) return;

  try {
    const payload = {
      title,
      description: description || null,
      priority,
      deadline: deadline || null,
      url: document.getElementById('task-url').value.trim() || null,
    };

    if (taskId) {
      await updateTask(columnId, taskId, payload);
    } else {
      await createTask(columnId, title, description, priority, deadline);
    }
    closeTaskModal();
    await loadBoard();
  } catch (err) {
    alert('Failed to save task: ' + err.message);
  }
});

// =====================
// Column CRUD
// =====================
async function addColumn(title) {
  const col = await api('POST', `${API_BASE}/api/columns`, { title });
  return col;
}

async function renameColumn(columnId) {
  const col = state.columns.find(c => c.id === columnId);
  if (!col) return;

  const newTitle = prompt('Rename column:', col.title);
  if (newTitle && newTitle.trim()) {
    try {
      await api('PATCH', `${API_BASE}/api/columns/${columnId}`, { title: newTitle.trim() });
      await loadBoard();
    } catch (err) {
      alert('Failed to rename column: ' + err.message);
    }
  }
}

async function deleteColumn(columnId) {
  const col = state.columns.find(c => c.id === columnId);
  if (!col) return;

  const msg = col.taskCount > 0
    ? `Delete "${col.title}" and its ${col.taskCount} task(s)?`
    : `Delete "${col.title}"?`;

  showConfirm('Delete Column', msg, async () => {
    try {
      await api('DELETE', `${API_BASE}/api/columns/${columnId}`);
      await loadBoard();
    } catch (err) {
      alert('Failed to delete column: ' + err.message);
    }
  });
}

function openColumnModal() {
  document.getElementById('column-title').value = '';
  columnModal.classList.remove('hidden');
  setTimeout(() => document.getElementById('column-title').focus(), 100);
}

function closeColumnModal() {
  columnModal.classList.add('hidden');
  columnForm.reset();
}

columnForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const title = document.getElementById('column-title').value.trim();
  if (!title) return;

  try {
    await addColumn(title);
    closeColumnModal();
    await loadBoard();
  } catch (err) {
    alert('Failed to create column: ' + err.message);
  }
});

// =====================
// Confirm Dialog
// =====================
let confirmCallback = null;

function showConfirm(title, message, onConfirm) {
  document.getElementById('confirm-title').textContent = title;
  document.getElementById('confirm-message').textContent = message;
  confirmCallback = onConfirm;
  confirmModal.classList.remove('hidden');
}

document.getElementById('confirm-yes').addEventListener('click', async () => {
  if (confirmCallback) await confirmCallback();
  confirmModal.classList.add('hidden');
  confirmCallback = null;
});

document.getElementById('confirm-no').addEventListener('click', () => {
  confirmModal.classList.add('hidden');
  confirmCallback = null;
});

// =====================
// Modal Close Handlers
// =====================
function setupModalClose(modalEl, ...closeFunctions) {
  modalEl.querySelectorAll('.modal-close, .modal-cancel').forEach(btn => {
    btn.addEventListener('click', () => closeFunctions.forEach(fn => fn()));
  });
}

setupModalClose(subtaskModal, closeSubtaskModal);
setupModalClose(taskModal, closeTaskModal);
setupModalClose(columnModal, closeColumnModal);
setupModalClose(timeModal, closeTimeModal);
setupModalClose(logModal, closeLogModal);

taskModal.addEventListener('click', (e) => { if (e.target === taskModal) closeTaskModal(); });
columnModal.addEventListener('click', (e) => { if (e.target === columnModal) closeColumnModal(); });
timeModal.addEventListener('click', (e) => { if (e.target === timeModal) closeTimeModal(); });
subtaskModal.addEventListener('click', (e) => { if (e.target === subtaskModal) closeSubtaskModal(); });
logModal.addEventListener('click', (e) => { if (e.target === logModal) closeLogModal(); });
confirmModal.addEventListener('click', (e) => {
  if (e.target === confirmModal) { confirmModal.classList.add('hidden'); confirmCallback = null; }
});

document.addEventListener('click', closeTaskMenus);

// =====================
// Keyboard shortcuts
// =====================
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    closeTaskModal();
    closeColumnModal();
    closeTimeModal();
    closeSubtaskModal();
    closeLogModal();
    confirmModal.classList.add('hidden');
    confirmCallback = null;
  }
});

// =====================
// Add Column Button
// =====================
document.getElementById('add-column-btn').addEventListener('click', openColumnModal);

// =====================
// Initialize
// =====================
loadBoard().catch(err => {
  console.error('Failed to load board:', err);
  // Show a message if server is not available
  boardEl.innerHTML = `
    <div style="padding: 40px; text-align: center;">
      <h2>⚠ Cannot connect to server</h2>
      <p style="color: #6b7280; margin-top: 8px;">
        Make sure the Java Spring Boot backend is running on port 8080.<br/>
        Error: ${err.message}
      </p>
    </div>`;
});
