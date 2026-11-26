// Exam Result Management System — Smooth Frontend Controller + Firebase Realtime Database Live Sync
// Dynamically grows & shrinks custom [Subject Name + Marks] rows and syncs in real time with Firebase RTDB.

const LOCAL_STORAGE_KEY = "exam_results_erp_v4";

let appRecords = [];
let nextErpId = "ERP";
let activeFirebaseUrl = null;
let realtimeConnected = false;
let firebaseUnsubscribe = null;

// DOM references
const statNextErp = document.getElementById("statNextErp");
const statTotal = document.getElementById("statTotal");
const statPass = document.getElementById("statPass");
const statFail = document.getElementById("statFail");
const dbStatusPill = document.getElementById("dbStatusPill");
const dbStatusText = document.getElementById("dbStatusText");
const formErpBadge = document.getElementById("formErpBadge");
const erpHintLabel = document.getElementById("erpHintLabel");
const subjectsCountPill = document.getElementById("subjectsCountPill");
const subjectRowsList = document.getElementById("subjectRowsList");
const statusBanner = document.getElementById("statusBanner");
const statusBannerText = document.getElementById("statusBannerText");
const tableMetaLabel = document.getElementById("tableMetaLabel");
const resultsTableHeadRow = document.getElementById("resultsTableHeadRow");
const resultsTableBody = document.getElementById("resultsTableBody");
const consoleBox = document.getElementById("consoleBox");

const erpIdInput = document.getElementById("erpIdInput");
const studentNameInput = document.getElementById("studentName");
const searchIdInput = document.getElementById("searchIdInput");

const FIREBASE_WEB_CONFIG = {
  apiKey: "AIzaSyCM_wupwp6qfxX8GEGtM1jmzfx_VXpG7ZA",
  authDomain: "exam-result-management-system.firebaseapp.com",
  databaseURL: "https://exam-result-management-system-default-rtdb.firebaseio.com",
  projectId: "exam-result-management-system",
  storageBucket: "exam-result-management-system.firebasestorage.app",
  messagingSenderId: "697046179729",
  appId: "1:697046179729:web:9683dd22c0087edff7c430",
};

// ---------- Firebase Realtime Database Live Subscription ----------
function setDbBadge(text, state) {
  dbStatusText.textContent = text;
  dbStatusPill.className = `db-status-pill ${state}`;
}

async function ensureFirebaseRealtimeSubscription(databaseURL = FIREBASE_WEB_CONFIG.databaseURL) {
  const targetUrl = databaseURL || FIREBASE_WEB_CONFIG.databaseURL;
  if (!targetUrl || targetUrl === activeFirebaseUrl) return;
  activeFirebaseUrl = targetUrl;

  try {
    const [{ initializeApp, getApps }, { getDatabase, ref, onValue }] = await Promise.all([
      import("https://www.gstatic.com/firebasejs/11.4.0/firebase-app.js"),
      import("https://www.gstatic.com/firebasejs/11.4.0/firebase-database.js"),
    ]);

    const existing = getApps();
    const app = existing.length
      ? existing[0]
      : initializeApp({ ...FIREBASE_WEB_CONFIG, databaseURL: targetUrl });
    const db = getDatabase(app, targetUrl);
    const resultsRef = ref(db, "examResults");

    if (firebaseUnsubscribe) firebaseUnsubscribe();

    firebaseUnsubscribe = onValue(
      resultsRef,
      (snapshot) => {
        realtimeConnected = true;
        setDbBadge("Firebase Realtime DB Live", "live");
        const val = snapshot.val();
        let rawList = [];
        if (val) {
          if (Array.isArray(val)) rawList = val;
          else if (Array.isArray(val.records)) rawList = val.records;
          else if (val.records && typeof val.records === "object") rawList = Object.values(val.records);
        }
        const normalized = rawList.filter(Boolean).map(normalizeRecordShape);
        let pass = 0;
        for (let i = 0; i < normalized.length; i++) {
          if (normalized[i].result === "Pass") pass++;
        }
        saveLocalFallback(normalized);
        renderState(
          {
            records: normalized,
            nextId: computeNextId(normalized),
            stats: { total: normalized.length, pass, fail: normalized.length - pass },
          },
          { syncErpInput: document.activeElement !== erpIdInput }
        );
      },
      () => {
        setDbBadge("Cloud Sync Active", "cloud");
      }
    );
  } catch {
    setDbBadge("Cloud API Active", "cloud");
  }
}

// ---------- Dynamic Auto-Grow / Auto-Shrink [Subject Name + Marks] Rows ----------
function createSubjectRow(index, initialSubject = "", initialMark = "", isOptional = false) {
  const row = document.createElement("div");
  row.className = isOptional ? "subject-entry-row optional-slot" : "subject-entry-row";

  const subInput = document.createElement("input");
  subInput.className = "text-input subject-name-input";
  subInput.type = "text";
  subInput.placeholder = isOptional ? "+ Next subject name" : `e.g. Subject ${index}`;
  subInput.value = initialSubject;

  const markInput = document.createElement("input");
  markInput.className = "text-input subject-mark-input";
  markInput.type = "text";
  markInput.inputMode = "numeric";
  markInput.placeholder = "0-100";
  markInput.value = initialMark;

  const removeRowBtn = document.createElement("button");
  removeRowBtn.type = "button";
  removeRowBtn.className = "row-remove-icon";
  removeRowBtn.title = "Remove this subject row";
  removeRowBtn.textContent = "✕";

  const onRowEdit = () => handleSubjectRowsChange();
  subInput.addEventListener("input", onRowEdit);
  markInput.addEventListener("input", onRowEdit);
  subInput.addEventListener("blur", () => pruneTrailingBlankSubjectRows(false));
  markInput.addEventListener("blur", () => pruneTrailingBlankSubjectRows(false));

  removeRowBtn.addEventListener("click", () => {
    const rows = getSubjectRowElements();
    if (rows.length <= 1) {
      subInput.value = "";
      markInput.value = "";
    } else {
      row.remove();
    }
    handleSubjectRowsChange();
  });

  row.appendChild(subInput);
  row.appendChild(markInput);
  row.appendChild(removeRowBtn);
  return row;
}

function getSubjectRowElements() {
  return Array.from(subjectRowsList.querySelectorAll(".subject-entry-row"));
}

function isRowEmpty(rowEl) {
  const s = rowEl.querySelector(".subject-name-input")?.value.trim() || "";
  const m = rowEl.querySelector(".subject-mark-input")?.value.trim() || "";
  return s === "" && m === "";
}

function refreshSubjectRowMetadata() {
  const rows = getSubjectRowElements();
  let activeCount = 0;

  for (let i = 0; i < rows.length; i++) {
    const subInp = rows[i].querySelector(".subject-name-input");
    const removeBtn = rows[i].querySelector(".row-remove-icon");
    const empty = isRowEmpty(rows[i]);
    if (!empty) activeCount++;

    const isTrailingOptional = i > 0 && i === rows.length - 1 && empty;
    rows[i].classList.toggle("optional-slot", isTrailingOptional);

    if (subInp) {
      subInp.placeholder = isTrailingOptional ? "+ Add another subject" : "e.g. DSA / Maths";
    }
    if (removeBtn) {
      removeBtn.style.visibility = isTrailingOptional && rows.length > 1 ? "hidden" : "visible";
    }
  }

  if (activeCount === 0) {
    subjectsCountPill.textContent = "0 subjects";
  } else if (activeCount === 1) {
    subjectsCountPill.textContent = "1 subject (/100)";
  } else {
    subjectsCountPill.textContent = `${activeCount} subjects (/${activeCount * 100})`;
  }
}

function handleSubjectRowsChange() {
  const rows = getSubjectRowElements();
  if (rows.length === 0) {
    subjectRowsList.appendChild(createSubjectRow(1, "", "", false));
    refreshSubjectRowMetadata();
    return;
  }

  const lastRow = rows[rows.length - 1];
  if (!isRowEmpty(lastRow)) {
    // Auto-Grow: append next optional subject row immediately
    subjectRowsList.appendChild(createSubjectRow(rows.length + 1, "", "", true));
  } else {
    // Auto-Shrink: remove extra empty trailing rows
    pruneTrailingBlankSubjectRows(true);
  }
  refreshSubjectRowMetadata();
}

function pruneTrailingBlankSubjectRows(protectFocused) {
  let rows = getSubjectRowElements();
  while (rows.length > 1) {
    const last = rows[rows.length - 1];
    const prev = rows[rows.length - 2];
    const focusInsideLast = last.contains(document.activeElement);

    if (isRowEmpty(last) && isRowEmpty(prev) && (!protectFocused || !focusInsideLast)) {
      last.remove();
      rows.pop();
    } else {
      break;
    }
  }
  refreshSubjectRowMetadata();
}

function resetSubjectRows() {
  subjectRowsList.innerHTML = "";
  subjectRowsList.appendChild(createSubjectRow(1, "", "", false));
  refreshSubjectRowMetadata();
}

function collectSubjectEntries() {
  return getSubjectRowElements().map((row) => ({
    subject: row.querySelector(".subject-name-input")?.value.trim() || "",
    mark: row.querySelector(".subject-mark-input")?.value.trim() || "",
  }));
}

// ---------- Record Normalization & Offline Fallback Engine ----------
function normalizeRecordShape(r) {
  let subjects = [];
  if (Array.isArray(r.subjects) && r.subjects.length > 0) {
    subjects = r.subjects.map((s, idx) => ({
      subject: String(s.subject || `Subject ${idx + 1}`).trim(),
      mark: Number(s.mark),
    }));
  } else if (Array.isArray(r.marks)) {
    subjects = r.marks.map((m, idx) => ({
      subject: `Subject ${idx + 1}`,
      mark: Number(m),
    }));
  }
  const marks = subjects.map((s) => s.mark);
  const maxMarks = r.maxMarks || subjects.length * 100;
  return {
    id: r.id,
    name: r.name,
    subjects,
    marks,
    total: r.total,
    maxMarks,
    percentage: Number(r.percentage),
    result: r.result,
  };
}

function parseErpInput(raw) {
  const trimmed = String(raw || "").trim();
  if (!trimmed) return null;
  const digits = trimmed.replace(/[^0-9]/g, "");
  if (!digits) return null;
  const n = parseInt(digits, 10);
  if (Number.isNaN(n) || n < 0) return null;
  return `ERP ${n}`;
}

function normalizeIdKey(raw) {
  const cleaned = String(raw || "").trim().toUpperCase().replace(/\s+/g, "");
  if (cleaned.startsWith("ERP")) return "ERP" + cleaned.slice(3);
  if (cleaned.startsWith("ER")) return "ERP" + cleaned.slice(2);
  if (/^\d+$/.test(cleaned)) return "ERP" + cleaned;
  return cleaned;
}

function computeNextId(list) {
  if (!list || list.length === 0) return "ERP";
  let max = null;
  for (let i = 0; i < list.length; i++) {
    const digits = String(list[i].id || "").replace(/[^0-9]/g, "");
    if (digits) {
      const n = parseInt(digits, 10);
      if (!Number.isNaN(n) && (max === null || n > max)) max = n;
    }
  }
  return max !== null ? `ERP ${max + 1}` : "ERP";
}

function fullDetail(r) {
  const norm = normalizeRecordShape(r);
  const subText = norm.subjects.map((s) => `${s.subject}: ${s.mark}`).join(", ");
  return `${norm.id} | ${norm.name} | [${subText}] | Total: ${norm.total}/${norm.maxMarks} | Percentage: ${norm.percentage.toFixed(2)} | Result: ${norm.result}`;
}

function sortDetail(r) {
  return `${r.id} | ${r.name} | ${Number(r.percentage).toFixed(2)} | ${r.result}`;
}

function linearSearchLocal(list, query) {
  const target = normalizeIdKey(query);
  if (!target || target === "ERP") return { record: null, index: -1 };
  for (let i = 0; i < list.length; i++) {
    if (normalizeIdKey(list[i].id) === target) {
      return { record: list[i], index: i };
    }
  }
  return { record: null, index: -1 };
}

function bubbleSortLocal(list) {
  const n = list.length;
  for (let i = 0; i < n - 1; i++) {
    let swapped = false;
    for (let j = 0; j < n - i - 1; j++) {
      if (list[j].percentage < list[j + 1].percentage) {
        const tmp = list[j];
        list[j] = list[j + 1];
        list[j + 1] = tmp;
        swapped = true;
      }
    }
    if (!swapped) break;
  }
}

function parseSubjectsClient(rawRows) {
  const rows = Array.isArray(rawRows)
    ? rawRows.map((x) => ({
        subject: String(x?.subject ?? "").replace(/[,|:]/g, " ").replace(/\s+/g, " ").trim(),
        markStr: String(x?.mark ?? "").trim(),
      }))
    : [];

  while (rows.length > 0 && rows[rows.length - 1].subject === "" && rows[rows.length - 1].markStr === "") {
    rows.pop();
  }
  if (rows.length === 0) {
    return { ok: false, subjects: [], error: "Please enter at least 1 subject name and its marks (0 - 100)." };
  }

  const subjects = [];
  const seen = [];
  for (let i = 0; i < rows.length; i++) {
    if (rows[i].subject === "" && rows[i].markStr === "") continue;
    if (rows[i].subject === "") {
      return { ok: false, subjects: [], error: `Please enter the subject name for row #${i + 1}.` };
    }
    if (rows[i].markStr === "") {
      return { ok: false, subjects: [], error: `Please enter marks (0 - 100) for "${rows[i].subject}".` };
    }
    const key = rows[i].subject.toLowerCase();
    if (seen.includes(key)) {
      return { ok: false, subjects: [], error: `Duplicate subject "${rows[i].subject}".` };
    }
    seen.push(key);
    const num = Number(rows[i].markStr);
    if (!Number.isInteger(num)) {
      return { ok: false, subjects: [], error: `Invalid marks for "${rows[i].subject}". Must be an integer 0 - 100.` };
    }
    if (num < 0 || num > 100) {
      return { ok: false, subjects: [], error: "Invalid marks. Each mark must be between 0 and 100." };
    }
    subjects.push({ subject: rows[i].subject, mark: num });
  }

  if (!subjects.length) {
    return { ok: false, subjects: [], error: "Please enter at least 1 subject name and its marks (0 - 100)." };
  }
  return { ok: true, subjects, error: "" };
}

function loadLocalFallback() {
  try {
    const raw = localStorage.getItem(LOCAL_STORAGE_KEY);
    return raw ? JSON.parse(raw).map(normalizeRecordShape) : [];
  } catch {
    return [];
  }
}

function saveLocalFallback(list) {
  try {
    localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(list));
  } catch {}
}

// ---------- API Client ----------
async function callBackend(method, payload) {
  try {
    const opts = { method, headers: {} };
    if (payload) {
      opts.headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(payload);
    }
    const res = await fetch("/api/results", opts);
    const data = await res.json();
    if (data.firebaseDatabaseUrl) {
      ensureFirebaseRealtimeSubscription(data.firebaseDatabaseUrl);
    } else if (!realtimeConnected) {
      setDbBadge("Cloud Storage Ready", "cloud");
    }
    if (Array.isArray(data.records)) {
      data.records = data.records.map(normalizeRecordShape);
      saveLocalFallback(data.records);
    }
    return data;
  } catch {
    setDbBadge("Offline Local Mode", "offline");
    return runLocalFallbackAction(method, payload);
  }
}

function runLocalFallbackAction(method, payload) {
  const list = loadLocalFallback();
  if (method === "GET") return buildClientPayload(list, true, "Ready.", -1);

  const action = String(payload?.action || "").toLowerCase();
  if (action === "add") {
    const rawErp = String(payload.erpId || "").trim();
    let id = null;
    if (rawErp && rawErp.toUpperCase() !== "ERP") {
      id = parseErpInput(rawErp);
      if (!id) return buildClientPayload(list, false, "Invalid ERP ID. Please enter a valid ERP number.", -1);
    } else {
      const autoId = computeNextId(list);
      if (autoId === "ERP") {
        return buildClientPayload(list, false, "Please enter the first ERP ID so the system can auto-detect subsequent ERP IDs.", -1);
      }
      id = autoId;
    }
    if (linearSearchLocal(list, id).record !== null) {
      return buildClientPayload(list, false, `Duplicate ERP ID (${id}). A record with this ID already exists.`, -1);
    }
    const name = String(payload.name || "").replace(/[,|:]/g, " ").replace(/\s+/g, " ").trim();
    if (!name) return buildClientPayload(list, false, "Invalid name. Name cannot be empty.", -1);

    const parsed = parseSubjectsClient(payload.subjects);
    if (!parsed.ok) return buildClientPayload(list, false, parsed.error, -1);

    let total = 0;
    const marks = [];
    for (let i = 0; i < parsed.subjects.length; i++) {
      marks.push(parsed.subjects[i].mark);
      total += parsed.subjects[i].mark;
    }
    const maxMarks = parsed.subjects.length * 100;
    const rawPct = (total * 100.0) / maxMarks;
    const rec = {
      id,
      name,
      subjects: parsed.subjects,
      marks,
      total,
      maxMarks,
      percentage: Number(rawPct.toFixed(2)),
      result: rawPct >= 40.0 ? "Pass" : "Fail",
    };
    list.push(rec);
    saveLocalFallback(list);
    return buildClientPayload(list, true, `Result added. ID: ${fullDetail(rec)}`, list.length - 1);
  }
  if (action === "remove") {
    const { record, index } = linearSearchLocal(list, payload.id);
    if (!record || index < 0) return buildClientPayload(list, false, `Result not found for ID: ${payload.id}`, -1);
    list.splice(index, 1);
    saveLocalFallback(list);
    return buildClientPayload(list, true, `Removed student record: ${record.id} | ${record.name}`, -1);
  }
  if (action === "search") {
    const q = String(payload.id || "").trim();
    const { record, index } = linearSearchLocal(list, q);
    if (record) return buildClientPayload(list, true, `Found: ${fullDetail(record)}`, index);
    return buildClientPayload(list, false, `Result not found for ID: ${q}`, -1);
  }
  if (action === "sort") {
    if (!list.length) return buildClientPayload(list, false, "No results found.", -1);
    bubbleSortLocal(list);
    saveLocalFallback(list);
    const lines = list.map((r, idx) => `${idx + 1}. ${sortDetail(r)}`).join("\n");
    return buildClientPayload(list, true, lines, 0);
  }
  if (action === "clear") {
    list.length = 0;
    saveLocalFallback(list);
    return buildClientPayload(list, true, "Cleared all saved records. Auto-detected ERP ID reset to ERP.", -1);
  }
  return buildClientPayload(list, false, "Unsupported action.", -1);
}

function buildClientPayload(list, success, message, matchedIndex) {
  let pass = 0;
  for (let i = 0; i < list.length; i++) if (list[i].result === "Pass") pass++;
  return {
    success,
    message,
    nextId: computeNextId(list),
    matchedIndex,
    stats: { total: list.length, pass, fail: list.length - pass },
    records: list,
  };
}

// ---------- UI Rendering Helpers ----------
function setBanner(text, variant = "info") {
  statusBanner.className = `status-banner ${variant}`;
  statusBannerText.textContent = text;
}

function appendConsole(text) {
  if (!consoleBox.textContent) {
    consoleBox.textContent = text;
  } else {
    consoleBox.textContent += "\n" + text;
  }
  consoleBox.scrollTop = consoleBox.scrollHeight;
}

function showToast(text, variant = "ok") {
  const stack = document.getElementById("toastStack");
  const el = document.createElement("div");
  el.className = `toast ${variant}`;
  el.textContent = text;
  stack.appendChild(el);
  setTimeout(() => {
    el.style.opacity = "0";
    el.style.transition = "opacity 0.25s ease";
    setTimeout(() => el.remove(), 260);
  }, 2800);
}

function bumpStat(el) {
  const box = el.closest(".stat-box");
  if (!box) return;
  box.classList.remove("bump");
  void box.offsetWidth;
  box.classList.add("bump");
  setTimeout(() => box.classList.remove("bump"), 250);
}

function syncErpFormField() {
  if (appRecords.length === 0 || nextErpId === "ERP") {
    erpHintLabel.textContent = "— enter first ID";
    erpIdInput.placeholder = "Enter ERP ID";
    erpIdInput.value = "";
  } else {
    erpHintLabel.textContent = "— auto-detected";
    erpIdInput.placeholder = nextErpId;
    erpIdInput.value = nextErpId;
  }
}

/**
 * Collects unique subject column names across all saved records (preserving order, case-insensitive match).
 */
function collectUniqueSubjectColumns(records) {
  const cols = [];
  const lowerKeys = [];
  for (let i = 0; i < records.length; i++) {
    const subs = records[i].subjects || [];
    for (let j = 0; j < subs.length; j++) {
      const name = String(subs[j].subject || "").trim();
      if (!name) continue;
      const key = name.toLowerCase();
      if (!lowerKeys.includes(key)) {
        lowerKeys.push(key);
        cols.push(name);
      }
    }
  }
  return cols;
}

function findSubjectMarkForRecord(record, colName) {
  const target = colName.toLowerCase();
  const subs = record.subjects || [];
  for (let i = 0; i < subs.length; i++) {
    if (String(subs[i].subject || "").trim().toLowerCase() === target) {
      return subs[i].mark;
    }
  }
  return null;
}

function renderDynamicTableHeader(subjectColumns) {
  if (subjectColumns.length === 0) {
    resultsTableHeadRow.innerHTML = `
      <th class="center" style="width:46px;">#</th>
      <th class="center" style="width:110px;">ERP ID</th>
      <th>Student Name</th>
      <th class="center">Subjects</th>
      <th class="center" style="width:100px;">Total</th>
      <th class="center" style="width:105px;">Percentage</th>
      <th class="center" style="width:88px;">Result</th>
      <th class="center" style="width:82px;">Action</th>
    `;
    return;
  }

  let subHeaders = "";
  for (let s = 0; s < subjectColumns.length; s++) {
    subHeaders += `<th class="center" style="min-width:84px;">${escapeHtml(subjectColumns[s])}</th>`;
  }

  resultsTableHeadRow.innerHTML = `
    <th class="center" style="width:46px;">#</th>
    <th class="center" style="width:110px;">ERP ID</th>
    <th style="min-width:140px;">Student Name</th>
    ${subHeaders}
    <th class="center" style="min-width:96px;">Total</th>
    <th class="center" style="min-width:96px;">Percentage</th>
    <th class="center" style="width:88px;">Result</th>
    <th class="center" style="width:82px;">Action</th>
  `;
}

function renderState(data, { highlightIndex = -1, metaLabel = null, syncErpInput = true } = {}) {
  appRecords = (data.records || []).map(normalizeRecordShape);
  nextErpId = data.nextId || computeNextId(appRecords);

  statNextErp.textContent = nextErpId;
  formErpBadge.textContent = nextErpId;
  statTotal.textContent = String(data.stats?.total ?? appRecords.length);
  statPass.textContent = String(data.stats?.pass ?? 0);
  statFail.textContent = String(data.stats?.fail ?? 0);
  bumpStat(statTotal);

  if (syncErpInput) {
    syncErpFormField();
  }

  tableMetaLabel.textContent = metaLabel || `Showing ${appRecords.length} record(s)`;

  const subjectColumns = collectUniqueSubjectColumns(appRecords);
  renderDynamicTableHeader(subjectColumns);

  if (appRecords.length === 0) {
    resultsTableBody.innerHTML = `
      <tr class="empty-row">
        <td colspan="8">No student results saved yet. Enter an ERP ID, student name, and subject names + marks on the left to begin.</td>
      </tr>`;
    return;
  }

  resultsTableBody.innerHTML = "";
  for (let i = 0; i < appRecords.length; i++) {
    const r = appRecords[i];
    const tr = document.createElement("tr");
    tr.style.animationDelay = `${Math.min(i * 0.025, 0.25)}s`;
    if (i === highlightIndex) tr.classList.add("matched-row");

    const pctText = Number(r.percentage).toFixed(2);
    const pillClass = r.result === "Pass" ? "pass" : "fail";

    let markCellsHtml = "";
    for (let s = 0; s < subjectColumns.length; s++) {
      const m = findSubjectMarkForRecord(r, subjectColumns[s]);
      markCellsHtml += `<td class="center">${m !== null ? m : "—"}</td>`;
    }

    tr.innerHTML = `
      <td class="center">${i + 1}</td>
      <td class="center erp-cell">${escapeHtml(r.id)}</td>
      <td>${escapeHtml(r.name)}</td>
      ${markCellsHtml}
      <td class="center"><strong>${r.total}</strong>/${r.maxMarks}</td>
      <td class="center">${pctText}</td>
      <td class="center"><span class="result-pill ${pillClass}">${r.result}</span></td>
      <td class="center"><button type="button" class="btn-row-remove" data-remove-id="${escapeHtml(r.id)}">Remove</button></td>
    `;

    tr.querySelector(".btn-row-remove").addEventListener("click", () => handleRemoveRecord(r.id));
    resultsTableBody.appendChild(tr);

    if (i === highlightIndex) {
      setTimeout(() => tr.scrollIntoView({ behavior: "smooth", block: "nearest" }), 60);
    }
  }
}

async function handleRemoveRecord(erpId) {
  const data = await callBackend("POST", { action: "remove", id: erpId });
  appendConsole(`> Remove Student (${erpId})`);
  appendConsole(data.message);
  if (data.success) {
    renderState(data, { syncErpInput: true });
    setBanner(data.message, "ok");
    showToast(`Removed ${erpId}`, "ok");
  } else {
    setBanner(data.message, "err");
    showToast(data.message, "err");
  }
}

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

// Live preview in form badge when user types an ERP ID
erpIdInput.addEventListener("input", () => {
  const parsed = parseErpInput(erpIdInput.value);
  formErpBadge.textContent = parsed || nextErpId;
});

// ---------- Event Listeners ----------
document.getElementById("addResultForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = {
    action: "add",
    erpId: erpIdInput.value,
    name: studentNameInput.value,
    subjects: collectSubjectEntries(),
  };
  const data = await callBackend("POST", payload);
  appendConsole("> Menu Option 1 (Add Result)");
  appendConsole(data.message);

  if (data.success) {
    renderState(data, { highlightIndex: data.matchedIndex, syncErpInput: true });
    setBanner(data.message, "ok");
    showToast(`Added ${data.records[data.records.length - 1]?.id}`, "ok");
    studentNameInput.value = "";
    resetSubjectRows();
    studentNameInput.focus();
  } else {
    setBanner(data.message, "err");
    showToast(data.message, "err");
    if (appRecords.length === 0 && !parseErpInput(erpIdInput.value)) {
      erpIdInput.focus();
    }
  }
});

document.getElementById("clearFormBtn").addEventListener("click", () => {
  studentNameInput.value = "";
  resetSubjectRows();
  syncErpFormField();
  formErpBadge.textContent = nextErpId;
  if (appRecords.length === 0) {
    erpIdInput.focus();
  } else {
    studentNameInput.focus();
  }
});

document.getElementById("searchForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const query = searchIdInput.value.trim();
  if (!query) {
    setBanner("Please enter an ERP ID to search.", "err");
    return;
  }
  const data = await callBackend("POST", { action: "search", id: query });
  appendConsole(`> Menu Option 3 (Search by ID: ${query})`);
  appendConsole(data.message);

  if (data.success) {
    renderState(data, {
      highlightIndex: data.matchedIndex,
      metaLabel: `Search matched row #${data.matchedIndex + 1} (${appRecords[data.matchedIndex]?.id || query})`,
      syncErpInput: false,
    });
    setBanner(data.message, "ok");
  } else {
    renderState(data, { highlightIndex: -1, syncErpInput: false });
    setBanner(data.message, "err");
    showToast(data.message, "err");
  }
});

document.getElementById("displayAllBtn").addEventListener("click", async () => {
  const data = await callBackend("GET");
  renderState(data, { metaLabel: `2 – Display All (${data.records.length} record(s))` });
  appendConsole("> Menu Option 2 (Display All)");
  if (data.records.length === 0) {
    appendConsole("No results found.");
    setBanner("No results found.", "info");
  } else {
    const lines = data.records.map((r, i) => `${i + 1}. ${fullDetail(r)}`).join("\n");
    appendConsole(lines);
    setBanner(`Displaying all ${data.records.length} student result(s).`, "info");
  }
});

document.getElementById("sortBtn").addEventListener("click", async () => {
  const data = await callBackend("POST", { action: "sort" });
  appendConsole("> Menu Option 4 (Sort by Percentage - high to low)");
  appendConsole(data.message);

  if (data.success) {
    renderState(data, {
      highlightIndex: 0,
      metaLabel: "Sorted by Percentage: High to Low",
    });
    const top = data.records[0];
    setBanner(`Sorted by Percentage (high to low). Top: ${sortDetail(top)}`, "ok");
    showToast("Sorted High to Low", "ok");
  } else {
    setBanner(data.message, "info");
  }
});

document.getElementById("downloadTxtBtn").addEventListener("click", () => {
  if (!appRecords.length) {
    showToast("No records to export yet.", "err");
    return;
  }
  const content = appRecords
    .map((r) => {
      const subPairs = r.subjects.map((s) => `${s.subject}:${s.mark}`).join(",");
      return `${r.id},${r.name},${subPairs},${r.total},${Number(r.percentage).toFixed(2)},${r.result}`;
    })
    .join("\n") + "\n";
  const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "results.txt";
  a.click();
  URL.revokeObjectURL(url);
  showToast("Exported results.txt", "ok");
});

// Clear All Confirmation Modal
const confirmModal = document.getElementById("confirmModal");
document.getElementById("clearAllBtn").addEventListener("click", () => {
  confirmModal.classList.add("open");
});
document.getElementById("cancelClearBtn").addEventListener("click", () => {
  confirmModal.classList.remove("open");
});
document.getElementById("confirmClearBtn").addEventListener("click", async () => {
  confirmModal.classList.remove("open");
  const data = await callBackend("POST", { action: "clear" });
  searchIdInput.value = "";
  renderState(data, { syncErpInput: true });
  appendConsole(`> ${data.message}`);
  setBanner(data.message, "ok");
  showToast("Cleared all saved records", "ok");
});

document.getElementById("clearConsoleBtn").addEventListener("click", () => {
  consoleBox.textContent = "";
});

// Theme Toggle
const themeToggleBtn = document.getElementById("themeToggleBtn");
themeToggleBtn.addEventListener("click", () => {
  const html = document.documentElement;
  const next = html.getAttribute("data-theme") === "dark" ? "light" : "dark";
  html.setAttribute("data-theme", next);
  themeToggleBtn.textContent = next === "dark" ? "☀️ Light" : "🌙 Dark";
});

// Initial Bootstrap
resetSubjectRows();
ensureFirebaseRealtimeSubscription();
(async function init() {
  const data = await callBackend("GET");
  renderState(data, { syncErpInput: true });
  if (data.records.length === 0) {
    setBanner("Ready. Enter ERP ID, student name, and each subject name + marks (0 - 100).", "info");
    appendConsole("System ready. 0 existing records. Enter the first ERP ID and subject names + marks to begin.");
  } else {
    setBanner(`Loaded ${data.records.length} record(s). Auto-detected next ID: ${data.nextId}`, "ok");
    appendConsole(`Loaded ${data.records.length} record(s):`);
    appendConsole(data.records.map((r, i) => `${i + 1}. ${fullDetail(r)}`).join("\n"));
  }
})();
